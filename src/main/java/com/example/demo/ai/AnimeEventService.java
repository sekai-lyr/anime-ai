package com.example.demo.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AnimeEventService {
    private static final String SOURCE_URL = "https://yunmanzhan.com/index.php?city=%s&page=1";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final Pattern EVENT_ROW = Pattern.compile(
            "<tr>\\s*<td>.*?<strong>(.*?)</strong>.*?</td>\\s*<td>(.*?)</td>\\s*<td>.*?<div class=\"venue-text\">(.*?)</div>.*?</td>\\s*<td>(.*?)</td>.*?<td><span class=\"badge ([^\"]+)\">(.*?)</span></td>\\s*</tr>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DATE_RANGE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})\\s*至\\s*(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final String BILIBILI_SEARCH_URL = "https://api.bilibili.com/x/web-interface/search/type?search_type=video&page=1&keyword=%s";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private volatile CacheEntry cache;

    public String searchUpcoming(String city, int limit) {
        return searchUpcoming(city, limit, false);
    }

    public String searchUpcoming(String city, int limit, boolean pureAnime) {
        return searchUpcoming(city, limit, pureAnime, null);
    }

    private String searchUpcoming(String city, int limit, boolean pureAnime, String eventFocus) {
        String normalizedCity = city == null || city.isBlank() ? "杭州" : city.trim().replace("市", "");
        String sourceUrl = SOURCE_URL.formatted(URLEncoder.encode(normalizedCity, StandardCharsets.UTF_8));
        try {
            Matcher matcher = EVENT_ROW.matcher(load(sourceUrl));
            LocalDate today = LocalDate.now();
            Set<String> events = new LinkedHashSet<>();
            while (matcher.find() && events.size() < Math.max(1, limit)) {
                String title = text(matcher.group(1));
                String dateText = text(matcher.group(2));
                String venue = text(matcher.group(3));
                String price = text(matcher.group(4));
                String statusClass = matcher.group(5);
                String status = text(matcher.group(6));
                Matcher dates = DATE_RANGE.matcher(dateText);
                if (!dates.find() || LocalDate.parse(dates.group(2)).isBefore(today)
                        || statusClass.contains("ended") || statusClass.contains("cancelled") || title.contains("取消")) continue;
                if (pureAnime && isGameFocused(title)) continue;
                if (eventFocus != null && !title.contains(eventFocus)) continue;
                String date = dates.group(1).equals(dates.group(2)) ? dates.group(1) : dates.group(1) + " 至 " + dates.group(2);
                events.add("- **" + title + "**\n  日期：" + date + "｜城市：" + normalizedCity
                        + "｜场馆：" + (venue.isBlank() ? "待公布" : venue)
                        + "｜票价：" + (price.isBlank() ? "待公布" : price) + "｜状态：" + status);
            }
            if (events.isEmpty()) return "目前没有查到" + normalizedCity + "“" + (eventFocus == null ? "" : eventFocus)
                    + "”下一届已确认的日期、场馆或售票公告。不是说不会再开，而是当前公开信息里尚未官宣。\n来源：" + sourceUrl;
            return normalizedCity + "近期后续" + (eventFocus == null ? "" : "“" + eventFocus + "”相关")
                    + (pureAnime ? "纯动漫/同人向" : "") + "漫展（按日期排序）：\n\n" + String.join("\n", events)
                    + "\n\n活动页：" + sourceUrl + "\n提示：漫展可能延期或取消，出发前请再次核对主办方公告。";
        } catch (Exception error) {
            return "漫展信息源暂时不可用，请稍后重试。原因：" + error.getMessage();
        }
    }

    public String searchIntelligence(String city, String keywords, int limit) {
        boolean pureAnime = keywords != null && (keywords.contains("纯漫展") || keywords.contains("二次元浓度")
                || keywords.contains("排除二游") || keywords.contains("不想看") || keywords.contains("同人向"));
        String eventFocus = extractEventFocus(keywords);
        String confirmed = searchUpcoming(city, limit, pureAnime, eventFocus);
        String bilibili = searchBilibili(city, eventFocus, 4);
        String organizer = eventFocus == null ? "" : searchBilibiliOrganizer(city, eventFocus);
        return confirmed + "\n\n### B站近期情报线索\n" + bilibili
                + (eventFocus == null ? "" : "\n\n### 主办方账号发现\n" + organizer
                + "\n\n### 官方源与票务入口\n" + buildSourceDiscoveryLinks(city, eventFocus))
                + "\n\n说明：上方结构化活动用于确认日期/场馆；B站用于补充最新排期、现场反馈、嘉宾与延期消息，重要信息需回到主办方公告复核。";
    }

    private String searchBilibili(String city, String eventFocus, int limit) {
        String normalizedCity = city == null || city.isBlank() ? "全国" : city.trim().replace("市", "");
        String query = eventFocus == null
                ? normalizedCity + " 漫展 " + LocalDate.now().getYear() + " 排期"
                : normalizedCity + " " + eventFocus + "漫展 下一届 官宣";
        String url = BILIBILI_SEARCH_URL.formatted(URLEncoder.encode(query, StandardCharsets.UTF_8));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 AnimeAI/1.0")
                    .header("Referer", "https://search.bilibili.com/")
                    .header("Accept", "application/json, text/plain, */*").GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) return "B站接口当前触发访问保护（HTTP " + response.statusCode()
                    + "），可直接打开搜索页继续查看：\nhttps://search.bilibili.com/all?keyword="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(new String(response.body(), StandardCharsets.UTF_8));
            JSONArray results = root.getJSONObject("data") == null ? null : root.getJSONObject("data").getJSONArray("result");
            if (results == null || results.isEmpty()) return "暂未找到相关 B站内容。\n搜索页：https://search.bilibili.com/all?keyword="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            StringBuilder output = new StringBuilder();
            int count = 0;
            for (int index = 0; index < results.size() && count < limit; index++) {
                JSONObject item = results.getJSONObject(index);
                String title = text(item.getString("title"));
                String description = text(item.getString("description"));
                String tags = item.getString("tag");
                String combined = title + " " + description + " " + (tags == null ? "" : tags);
                if (!combined.contains("漫展") && !combined.contains("动漫展") && !combined.contains("同人")) continue;
                if (!"全国".equals(normalizedCity) && !combined.contains(normalizedCity)) continue;
                if (eventFocus != null && !combined.contains(eventFocus)) continue;
                long timestamp = item.getLongValue("pubdate");
                LocalDate published = Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
                if (published.isBefore(LocalDate.now().minusMonths(8))) continue;
                String bvid = item.getString("bvid");
                String summary = description.length() > 90 ? description.substring(0, 90) + "…" : description;
                output.append("- [").append(title).append("](https://www.bilibili.com/video/").append(bvid).append(")")
                        .append("｜UP：").append(item.getString("author"))
                        .append("（[主页](https://space.bilibili.com/").append(item.getLongValue("mid")).append(")）")
                        .append("｜发布：").append(published.format(DateTimeFormatter.ISO_DATE));
                if (eventFocus != null && item.getString("author") != null
                        && item.getString("author").replace("动漫展", "").contains(eventFocus)) {
                    output.append("｜**疑似主办方官方账号，请结合认证信息核验**");
                }
                if (!summary.isBlank()) output.append("\n  线索摘要：").append(summary.replace("\n", "；"));
                output.append("\n");
                count++;
            }
            if (count == 0) return "B站暂未找到与“" + normalizedCity + "”直接相关的有效漫展线索。\n搜索页：https://search.bilibili.com/all?keyword="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            return output.toString().trim();
        } catch (Exception error) {
            return "B站实时检索暂时不可用：" + error.getMessage();
        }
    }

    private String load(String url) throws Exception {
        CacheEntry current = cache;
        if (current != null && current.url.equals(url) && current.loadedAt.plus(CACHE_TTL).isAfter(Instant.now())) return current.html;
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Mozilla/5.0 AnimeAI/1.0").GET().build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + response.statusCode());
        String html = new String(response.body(), StandardCharsets.UTF_8);
        cache = new CacheEntry(url, html, Instant.now());
        return html;
    }

    private String text(String html) {
        return TAG.matcher(html).replaceAll(" ").replace("&nbsp;", " ").replace("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
    }

    private boolean isGameFocused(String title) {
        String[] gameWords = {"游戏", "洛克王国", "鸣潮", "明日方舟", "马娘", "赛马娘", "王者荣耀",
                "第五人格", "蔚蓝档案", "原神", "崩坏", "星穹铁道", "绝区零", "恋与深空"};
        for (String word : gameWords) if (title.contains(word)) return true;
        return false;
    }

    private String extractEventFocus(String keywords) {
        if (keywords == null || keywords.isBlank()) return null;
        Matcher matcher = Pattern.compile("([\\p{IsHan}A-Za-z0-9·]{2,14})(?:动漫展|漫展|同人展)").matcher(keywords);
        String focus = null;
        while (matcher.find()) {
            String candidate = matcher.group(1);
            int possessive = candidate.lastIndexOf('的');
            if (possessive >= 0) candidate = candidate.substring(possessive + 1);
            candidate = candidate.replaceFirst("^(?:杭州|上海|北京|广州|深圳|成都|重庆|武汉|南京|苏州)", "");
            if (!candidate.isBlank() && !Set.of("纯", "最近", "后面", "接下来", "动漫", "中国").contains(candidate)) focus = candidate;
        }
        return focus;
    }

    private String buildSourceDiscoveryLinks(String city, String eventFocus) {
        String normalizedCity = city == null ? "" : city.trim().replace("市", "");
        String query = (normalizedCity + " " + eventFocus + "漫展 官方 官宣 票务").trim();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        return "- [B站官宣/主办方搜索](https://search.bilibili.com/all?keyword=" + encoded + ")\n"
                + "- [微博官方动态搜索](https://s.weibo.com/weibo?q=" + encoded + ")\n"
                + "- [抖音官方动态搜索](https://www.douyin.com/search/" + encoded + ")\n"
                + "- [小红书官方动态搜索](https://www.xiaohongshu.com/search_result?keyword=" + encoded + ")\n"
                + "- [B站会员购票务搜索](https://search.bilibili.com/all?keyword="
                + URLEncoder.encode(normalizedCity + " " + eventFocus + "漫展 会员购 票务", StandardCharsets.UTF_8) + ")\n"
                + "这些是精确检索入口，不等于官方认证；工具会优先展示账号认证、主办方自述和票务详情能够互相印证的来源。";
    }

    private String searchBilibiliOrganizer(String city, String eventFocus) {
        String normalizedCity = city == null ? "" : city.trim().replace("市", "");
        String query = normalizedCity + eventFocus + "动漫展";
        String url = "https://api.bilibili.com/x/web-interface/search/type?search_type=bili_user&page=1&keyword="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                    .header("User-Agent", "Mozilla/5.0 AnimeAI/1.0")
                    .header("Referer", "https://search.bilibili.com/")
                    .header("Accept", "application/json, text/plain, */*").GET().build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) return "B站用户检索触发访问保护，暂时无法自动确认主办方账号。";
            JSONObject root = JSON.parseObject(new String(response.body(), StandardCharsets.UTF_8));
            JSONArray results = root.getJSONObject("data") == null ? null : root.getJSONObject("data").getJSONArray("result");
            if (results == null || results.isEmpty()) return "没有发现名称直接匹配的 B站主办方账号，请使用下方精确入口继续核验。";
            for (int index = 0; index < Math.min(results.size(), 5); index++) {
                JSONObject user = results.getJSONObject(index);
                String name = text(user.getString("uname"));
                String signature = text(user.getString("usign"));
                if (!name.contains(eventFocus) && !signature.contains(eventFocus)) continue;
                JSONObject verification = user.getJSONObject("official_verify");
                int verificationType = verification == null ? -1 : verification.getIntValue("type");
                boolean verified = verificationType == 0 || verificationType == 1;
                StringBuilder account = new StringBuilder("- [").append(name).append("](https://space.bilibili.com/")
                        .append(user.getLongValue("mid")).append(")")
                        .append("｜粉丝：").append(user.getLongValue("fans"))
                        .append("｜B站认证：").append(verified ? "有" : "未显示")
                        .append("｜账号判断：").append(verified ? "认证信息与名称匹配" : "名称和简介高度匹配，仍需在主页核验");
                if (!signature.isBlank()) account.append("\n  简介：").append(signature);
                JSONArray submissions = user.getJSONArray("res");
                if (submissions != null && !submissions.isEmpty()) {
                    account.append("\n  已核查该账号最近投稿：");
                    for (int videoIndex = 0; videoIndex < Math.min(3, submissions.size()); videoIndex++) {
                        JSONObject video = submissions.getJSONObject(videoIndex);
                        LocalDate published = Instant.ofEpochSecond(video.getLongValue("pubdate"))
                                .atZone(ZoneId.systemDefault()).toLocalDate();
                        account.append("\n  - [").append(text(video.getString("title"))).append("](https://www.bilibili.com/video/")
                                .append(video.getString("bvid")).append(")｜").append(published);
                    }
                }
                return account.toString();
            }
            return "搜索到了相关用户，但名称、简介与展会名无法可靠对应，未标记为官方账号。";
        } catch (Exception error) {
            return "B站主办方账号检索暂时不可用：" + error.getMessage();
        }
    }

    private record CacheEntry(String url, String html, Instant loadedAt) {}
}
