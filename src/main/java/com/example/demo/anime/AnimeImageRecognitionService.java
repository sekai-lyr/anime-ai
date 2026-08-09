package com.example.demo.anime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.demo.vision.VisionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnimeImageRecognitionService {

    private static final double RELIABLE_TRACE_SIMILARITY = 0.82;
    private static final int MAX_CHARACTER_CANDIDATES = 12;

    private final VisionService visionService;
    private final LocalCharacterIndexService localCharacterIndexService;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    public String recognize(String imageDataUrl) throws Exception {
        ImageData image = decodeImage(imageDataUrl);
        TraceMatch match = searchTraceMoe(image);
        if (match == null || match.similarity() < RELIABLE_TRACE_SIMILARITY) {
            log.info("No reliable trace.moe match, using current AniList catalog comparison");
            Integer currentAnimeId = identifyFromVisibleTitle(imageDataUrl);
            if (currentAnimeId == null) currentAnimeId = localCharacterIndexService.identifyAnime(imageDataUrl);
            if (currentAnimeId == null) currentAnimeId = identifyFromRecentTitleCatalog(imageDataUrl);
            if (currentAnimeId == null) currentAnimeId = identifyFromCurrentCatalog(imageDataUrl);
            if (currentAnimeId == null) currentAnimeId = identifyFromModelCandidates(imageDataUrl);
            if (currentAnimeId == null) return noReliableMatch();
            AnimeCandidates candidates = loadAniListCandidates(currentAnimeId);
            return compareCharacters(imageDataUrl, candidates, null);
        }

        AnimeCandidates candidates = loadAniListCandidates(match.anilistId());
        return compareCharacters(imageDataUrl, candidates, match);
    }

    private String compareCharacters(String imageDataUrl, AnimeCandidates candidates, TraceMatch match) throws Exception {
        if (candidates.characters().isEmpty()) {
            return identifyWithoutReferences(imageDataUrl, match, candidates.title());
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append("任务：只识别主截图中的动漫角色，并给出所属番剧。\n")
                .append("实时匹配番剧：").append(candidates.title()).append("\n");
        if (match != null) {
            prompt.append("trace.moe 相似度：").append(Math.round(match.similarity() * 100)).append("%\n")
                    .append("集数：").append(match.episode()).append("，时间点：").append(formatTime(match.from())).append("\n");
        }
        prompt
                .append("下面按编号提供该番剧的实时角色候选图。请比较发型、瞳色、服装、脸部特征和配饰。\n")
                .append("只输出：角色、番剧、置信度、判断依据、其他可能候选。不要介绍剧情。\n")
                .append("候选：\n");

        List<String> referenceUrls = new ArrayList<>();
        for (int i = 0; i < candidates.characters().size(); i++) {
            CharacterCandidate character = candidates.characters().get(i);
            prompt.append(i + 1).append(". ").append(character.name())
                    .append("（").append(character.nativeName()).append("，")
                    .append(character.role()).append("）\n");
            referenceUrls.add(character.imageUrl());
        }

        return visionService.analyzeImageWithReferences(imageDataUrl, prompt.toString(), referenceUrls);
    }

    private Integer identifyFromVisibleTitle(String imageDataUrl) throws Exception {
        String extraction = visionService.analyzeImageWithCustomPrompt(imageDataUrl,
                "读取图片中可见的动漫日文、英文或中文标题。只输出 TITLE:标题；没有可识别标题则输出 TITLE:NONE。不要解释。");
        Matcher matcher = Pattern.compile("TITLE\\s*[:：]\\s*([^\\r\\n]+)", Pattern.CASE_INSENSITIVE).matcher(extraction);
        if (!matcher.find()) return null;
        String title = matcher.group(1).trim().replaceAll("^[《『\"']|[》』\"']$", "");
        if (title.equalsIgnoreCase("NONE") || title.length() < 2) return null;
        Integer id = searchAniListId(title);
        log.info("Visible title lookup: title={}, anilistId={}", title, id);
        return id;
    }

    private Integer searchAniListId(String title) throws Exception {
        String query = "query($title:String!){Page(perPage:1){media(search:$title,type:ANIME){id}}}";
        JSONObject payload = new JSONObject();
        payload.put("query", query);
        payload.put("variables", JSONObject.of("title", title));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://graphql.anilist.co"))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;
        JSONArray media = JSON.parseObject(response.body()).getJSONObject("data").getJSONObject("Page").getJSONArray("media");
        return media == null || media.isEmpty() ? null : media.getJSONObject(0).getInteger("id");
    }

    private Integer identifyFromCurrentCatalog(String imageDataUrl) throws Exception {
        int currentYear = LocalDate.now().getYear();
        for (int year = currentYear; year >= currentYear - 2; year--) {
            List<MediaCandidate> media = loadCurrentMedia(year);
            if (media.isEmpty()) continue;
            StringBuilder prompt = new StringBuilder("主图是待识别的动漫截图、宣传图或同人图，后续图片是")
                    .append(year).append("年番剧封面候选。请通过人物性别、发型、服装、画风、Logo和题材寻找来源。"
                            + "人物性别或核心外观冲突时必须选NONE。只能从候选中选；明显不符则选NONE。"
                            + "只输出 ANILIST_ID:数字 CONFIDENCE:0-100，或 ANILIST_ID:NONE。候选：\n");
            List<String> covers = new ArrayList<>();
            for (int i = 0; i < media.size(); i++) {
                MediaCandidate item = media.get(i);
                prompt.append(i + 1).append(". ID=").append(item.id()).append(" ").append(item.title()).append("\n");
                covers.add(item.coverUrl());
            }
            String result = visionService.analyzeImageWithReferences(imageDataUrl, prompt.toString(), covers);
            Matcher idMatcher = Pattern.compile("ANILIST_ID\\s*[:：]\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(result);
            Matcher confidenceMatcher = Pattern.compile("CONFIDENCE\\s*[:：]\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(result);
            if (!idMatcher.find() || !confidenceMatcher.find() || Integer.parseInt(confidenceMatcher.group(1)) < 80) continue;
            int id = Integer.parseInt(idMatcher.group(1));
            if (media.stream().anyMatch(item -> item.id() == id)) return id;
        }
        return null;
    }

    private Integer identifyFromRecentTitleCatalog(String imageDataUrl) throws Exception {
        int currentYear = LocalDate.now().getYear();
        List<MediaCandidate> media = new ArrayList<>();
        for (int year = currentYear; year >= currentYear - 2; year--) media.addAll(loadMedia(year, 50));
        if (media.isEmpty()) return null;
        StringBuilder prompt = new StringBuilder("只识别主图中的角色来源。下面是近三年 AniList 实时番剧候选。"
                + "先核对人物性别、运动/职业、服装、时代和画风，再选择最符合的作品。"
                + "核心特征冲突或证据不足必须选NONE。只输出 ANILIST_ID:数字 CONFIDENCE:0-100 或 ANILIST_ID:NONE。\n");
        for (MediaCandidate item : media) prompt.append(item.id()).append(" | ").append(item.title())
                .append(" | ").append(item.context()).append("\n");
        String result = visionService.analyzeImageWithCustomPrompt(imageDataUrl, prompt.toString());
        Matcher idMatcher = Pattern.compile("ANILIST_ID\\s*[:：]\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(result);
        Matcher confidenceMatcher = Pattern.compile("CONFIDENCE\\s*[:：]\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(result);
        if (!idMatcher.find() || !confidenceMatcher.find() || Integer.parseInt(confidenceMatcher.group(1)) < 85) return null;
        int id = Integer.parseInt(idMatcher.group(1));
        return media.stream().anyMatch(item -> item.id() == id) ? id : null;
    }

    private Integer identifyFromModelCandidates(String imageDataUrl) throws Exception {
        String proposal = visionService.analyzeImageWithCustomPrompt(imageDataUrl,
                "根据角色外观提出最多5个可能的日本动漫作品标题，可包含任何年代。先严格判断人物性别、年龄、发型、服装和题材。"
                        + "只输出 CANDIDATES:标题1|标题2|标题3；完全没有候选则输出 CANDIDATES:NONE。不要解释。");
        log.info("Cross-era candidate proposal: {}", proposal.replaceAll("\\s+", " "));
        Matcher matcher = Pattern.compile("CANDIDATES\\s*[:：]\\s*([^\\r\\n]+)", Pattern.CASE_INSENSITIVE).matcher(proposal);
        if (!matcher.find() || matcher.group(1).trim().equalsIgnoreCase("NONE")) return null;

        List<AnimeCandidates> shows = new ArrayList<>();
        for (String rawTitle : matcher.group(1).split("\\|")) {
            if (shows.size() >= 3) break;
            String title = rawTitle.trim().replaceAll("^[《『\"']|[》』\"']$", "");
            if (title.length() < 2) continue;
            Integer id = searchAniListId(title);
            if (id == null || shows.stream().anyMatch(show -> show.id() == id)) continue;
            AnimeCandidates show = loadAniListCandidates(id);
            if (!show.characters().isEmpty()) shows.add(show);
        }
        if (shows.isEmpty()) return null;

        StringBuilder prompt = new StringBuilder("主图是待识别图片。后续是视觉模型提出的跨年代候选作品角色图。"
                + "必须逐项核对人物性别、发型、瞳色、服装和角色数量；核心特征冲突必须淘汰。"
                + "只输出 MATCH_ID:作品ID CONFIDENCE:0-100，或 MATCH_ID:NONE。\n");
        List<String> references = new ArrayList<>();
        for (AnimeCandidates show : shows) {
            prompt.append("作品 ID=").append(show.id()).append(" 《").append(show.title()).append("》\n");
            for (int i = 0; i < Math.min(4, show.characters().size()); i++) {
                CharacterCandidate character = show.characters().get(i);
                prompt.append("- ").append(character.name()).append(" / ").append(character.nativeName()).append("\n");
                references.add(character.imageUrl());
            }
        }
        String verification = visionService.analyzeImageWithReferences(imageDataUrl, prompt.toString(), references);
        log.info("Cross-era candidate verification: {}", verification.replaceAll("\\s+", " "));
        Matcher idMatcher = Pattern.compile("MATCH_ID\\s*[:：]\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(verification);
        Matcher confidenceMatcher = Pattern.compile("CONFIDENCE\\s*[:：]\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(verification);
        if (!idMatcher.find() || !confidenceMatcher.find() || Integer.parseInt(confidenceMatcher.group(1)) < 85) return null;
        int id = Integer.parseInt(idMatcher.group(1));
        return shows.stream().anyMatch(show -> show.id() == id) ? id : null;
    }

    private List<MediaCandidate> loadCurrentMedia(int year) throws Exception {
        return loadMedia(year, 10);
    }

    private List<MediaCandidate> loadMedia(int year, int limit) throws Exception {
        String query = "query($year:Int!,$limit:Int!){Page(page:1,perPage:$limit){media(type:ANIME,seasonYear:$year,sort:[TRENDING_DESC,POPULARITY_DESC]){id title{romaji english native}genres description coverImage{extraLarge}}}}";
        JSONObject payload = new JSONObject();
        payload.put("query", query);
        payload.put("variables", JSONObject.of("year", year, "limit", limit));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://graphql.anilist.co"))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return List.of();
        JSONArray rows = JSON.parseObject(response.body()).getJSONObject("data").getJSONObject("Page").getJSONArray("media");
        List<MediaCandidate> result = new ArrayList<>();
        for (int i = 0; rows != null && i < rows.size(); i++) {
            JSONObject media = rows.getJSONObject(i);
            String cover = media.getJSONObject("coverImage").getString("extraLarge");
            if (cover == null || cover.isBlank()) continue;
            JSONObject titles = media.getJSONObject("title");
            String description = media.getString("description");
            if (description == null) description = "";
            description = description.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
            if (description.length() > 180) description = description.substring(0, 180);
            JSONArray genres = media.getJSONArray("genres");
            String context = (genres == null ? "" : genres.toJavaList(String.class)) + " " + description;
            result.add(new MediaCandidate(media.getIntValue("id"),
                    firstNonBlank(titles.getString("native"), titles.getString("english"), titles.getString("romaji")), cover, context));
        }
        return result;
    }

    private String noReliableMatch() {
        return "角色：无法可靠确认\n番剧：无法可靠确认\n置信度：低\n判断依据：正片识图、可见标题和近三年实时番剧目录均无可靠匹配，因此不进行模型猜测。";
    }

    private TraceMatch searchTraceMoe(ImageData image) throws Exception {
        String boundary = "----AnimeAI" + UUID.randomUUID();
        byte[] prefix = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"image\"; filename=\"screenshot\"\r\n"
                + "Content-Type: " + image.mimeType() + "\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[prefix.length + image.bytes().length + suffix.length];
        System.arraycopy(prefix, 0, body, 0, prefix.length);
        System.arraycopy(image.bytes(), 0, body, prefix.length, image.bytes().length);
        System.arraycopy(suffix, 0, body, prefix.length + image.bytes().length, suffix.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.trace.moe/search?cutBorders"))
                .timeout(Duration.ofSeconds(25))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", "AnimeAI/1.0")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("trace.moe request failed: status={}", response.statusCode());
            return null;
        }

        JSONArray results = JSON.parseObject(response.body()).getJSONArray("result");
        if (results == null || results.isEmpty()) return null;
        JSONObject top = results.getJSONObject(0);
        TraceMatch match = new TraceMatch(
                top.getIntValue("anilist"),
                top.getDoubleValue("similarity"),
                top.getString("episode"),
                top.getDoubleValue("from"));
        log.info("trace.moe matched: anilistId={}, similarity={}", match.anilistId(), match.similarity());
        return match;
    }

    private AnimeCandidates loadAniListCandidates(int anilistId) throws Exception {
        String query = "query($id:Int!){Media(id:$id,type:ANIME){title{romaji english native}characters(page:1,perPage:"
                + MAX_CHARACTER_CANDIDATES + ",sort:[ROLE,RELEVANCE,ID]){edges{role node{name{full native}image{large}}}}}}";
        JSONObject payload = new JSONObject();
        payload.put("query", query);
        payload.put("variables", JSONObject.of("id", anilistId));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://graphql.anilist.co"))
                .timeout(Duration.ofSeconds(12))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString()))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("AniList 查询失败: " + response.statusCode());

        JSONObject media = JSON.parseObject(response.body()).getJSONObject("data").getJSONObject("Media");
        JSONObject titles = media.getJSONObject("title");
        String title = firstNonBlank(titles.getString("native"), titles.getString("english"), titles.getString("romaji"));
        List<CharacterCandidate> characters = new ArrayList<>();
        JSONArray edges = media.getJSONObject("characters").getJSONArray("edges");
        if (edges != null) {
            for (int i = 0; i < edges.size(); i++) {
                JSONObject edge = edges.getJSONObject(i);
                JSONObject node = edge.getJSONObject("node");
                String imageUrl = node.getJSONObject("image").getString("large");
                if (imageUrl == null || imageUrl.isBlank()) continue;
                JSONObject names = node.getJSONObject("name");
                characters.add(new CharacterCandidate(
                        names.getString("full"),
                        firstNonBlank(names.getString("native"), names.getString("full")),
                        edge.getString("role"),
                        imageUrl));
            }
        }
        return new AnimeCandidates(anilistId, title, characters);
    }

    private String identifyWithoutReferences(String imageDataUrl, TraceMatch match, String title) throws Exception {
        String source = match == null ? "实时番剧目录匹配" : "实时反向识图匹配，相似度" + Math.round(match.similarity() * 100) + "%";
        String prompt = "只识别截图中的动漫角色。" + source + "到番剧《" + title
                + "》；请给出角色、番剧、置信度和依据。不能确认时不要猜。";
        return visionService.analyzeImageWithCustomPrompt(imageDataUrl, prompt);
    }

    private String visionFallback(String imageDataUrl) throws Exception {
        String prompt = "只识别截图中的动漫角色及所属番剧。当前实时数据库没有可靠结果，请根据角色外观、字幕和Logo判断。"
                + "必须核对人物性别、发色、服装和作品题材；任何核心特征冲突都不得输出高置信度。"
                + "只输出角色、番剧、置信度、判断依据、其他可能候选，总共不超过8行。不能确认时明确说明，不要介绍剧情或画面。";
        return visionService.analyzeImageWithCustomPrompt(imageDataUrl, prompt);
    }

    private ImageData decodeImage(String dataUrl) {
        int comma = dataUrl.indexOf(',');
        if (!dataUrl.startsWith("data:image/") || comma < 0) {
            throw new IllegalArgumentException("图片格式无效");
        }
        String metadata = dataUrl.substring(5, comma);
        String mimeType = metadata.substring(0, metadata.indexOf(';'));
        return new ImageData(mimeType, Base64.getDecoder().decode(dataUrl.substring(comma + 1)));
    }

    private String formatTime(double seconds) {
        int value = (int) seconds;
        return String.format("%02d:%02d", value / 60, value % 60);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "未知";
    }

    private record ImageData(String mimeType, byte[] bytes) {}
    private record TraceMatch(int anilistId, double similarity, String episode, double from) {}
    private record CharacterCandidate(String name, String nativeName, String role, String imageUrl) {}
    private record AnimeCandidates(int id, String title, List<CharacterCandidate> characters) {}
    private record MediaCandidate(int id, String title, String coverUrl, String context) {}
}
