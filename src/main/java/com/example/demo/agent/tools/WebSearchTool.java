package com.example.demo.agent.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class WebSearchTool extends BaseTool {

    private static final Logger logger = LoggerFactory.getLogger(WebSearchTool.class);
    private static final String TOOL_NAME = "webSearch";
    private static final String TOOL_DESCRIPTION = "联网搜索工具，获取实时新闻、动漫资讯、百科等在线信息";

    @Value("${baidu.search.api-key:}")
    private String apiKey;

    @Value("${baidu.search.api-url:https://api.baidu.com/json/snc/v1/search}")
    private String apiUrl;

    private final OkHttpClient httpClient;
    private final ToolDefinition toolDefinition;

    public WebSearchTool() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build();

        this.toolDefinition = ToolDefinition.builder()
                .name(TOOL_NAME)
                .description(TOOL_DESCRIPTION)
                .parameter("query", "string", "搜索关键词")
                .required("query")
                .build();
    }

    @Override public String getName() { return TOOL_NAME; }
    @Override public String getDescription() { return TOOL_DESCRIPTION; }
    @Override public ToolDefinition getDefinition() { return toolDefinition; }

    @Override
    public ToolResult<String> execute(JSONObject params) {
        String query = params.getString("query");
        if (query == null || query.trim().isEmpty()) {
            return ToolResult.failure("请提供搜索关键词");
        }

        logger.info("[WebSearch] Searching: {}", query);

        // Try Baidu API
        String result = tryBaiduSearch(query);
        if (result != null) return ToolResult.success(result);

        // Try Bing search (HTML scrape)
        result = tryBingSearch(query);
        if (result != null) return ToolResult.success(result);

        return ToolResult.success("🔍 关于「" + query + "」的联网搜索暂时不可用。建议尝试：\n"
                + "1. 使用更具体的关键词重试\n"
                + "2. 直接告诉我具体的动漫名称，我可以基于已知信息帮你介绍\n"
                + "3. 试试其他类型的查询，比如推荐、角色信息等");
    }

    private String tryBaiduSearch(String query) {
        try {
            JSONObject bodyMap = new JSONObject();
            bodyMap.put("query", query);
            bodyMap.put("count", 8);

            String jsonBody = JSON.toJSONString(bodyMap);
            RequestBody requestBody = RequestBody.create(
                    jsonBody, MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(apiUrl)
                    .header("x-api-key", apiKey != null ? apiKey : "")
                    .header("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JSONObject json = JSON.parseObject(body);
                    if (json == null) return null;

                    JSONObject header = json.getJSONObject("header");
                    if (header != null && header.getIntValue("status") != 0) return null;

                    JSONObject respBody = json.getJSONObject("body");
                    if (respBody != null && respBody.get("data") instanceof JSONArray) {
                        JSONArray dataArray = respBody.getJSONArray("data");
                        if (!dataArray.isEmpty()) return formatResults(dataArray, query);
                    }

                    JSONObject data = json.getJSONObject("data");
                    if (data != null) {
                        JSONArray list = data.getJSONArray("list");
                        if (list != null && !list.isEmpty()) return formatResults(list, query);
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("[WebSearch] Baidu failed: {}", e.getMessage());
        }
        return null;
    }

    private String tryBingSearch(String query) {
        try {
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            String url = "https://www.bing.com/search?q=" + encoded + "&count=10";

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String html = response.body().string();
                    return parseBingHtml(html, query);
                }
            }
        } catch (Exception e) {
            logger.debug("[WebSearch] Bing failed: {}", e.getMessage());
        }
        return null;
    }

    private String parseBingHtml(String html, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("📎 搜索结果：\n\n");

        // Bing uses <li class="b_algo"> for results
        // Title: <h2><a href="...">title</a></h2>
        // Snippet: <p> or <div class="b_caption">
        java.util.regex.Pattern algoPattern = java.util.regex.Pattern.compile(
                "<li[^>]*class=\"b_algo\"[^>]*>(.*?)</li>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);

        java.util.regex.Pattern linkPattern = java.util.regex.Pattern.compile(
                "<a[^>]*href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);

        java.util.regex.Pattern snippetPattern = java.util.regex.Pattern.compile(
                "<p[^>]*>(.*?)</p>|<div[^>]*class=\"[^\"]*b_caption[^\"]*\"[^>]*>(.*?)</div>",
                java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL);

        java.util.regex.Matcher algoMatcher = algoPattern.matcher(html);

        int count = 0;
        while (algoMatcher.find() && count < 5) {
            String block = algoMatcher.group(1);

            java.util.regex.Matcher linkMatcher = linkPattern.matcher(block);
            if (!linkMatcher.find()) continue;

            String url = linkMatcher.group(1);
            String title = linkMatcher.group(2).replaceAll("<[^>]+>", "").trim();
            if (title.length() < 2) continue;

            count++;
            sb.append(count).append(". **").append(title).append("**\n");

            java.util.regex.Matcher snippetMatcher = snippetPattern.matcher(block);
            if (snippetMatcher.find()) {
                String snippet = snippetMatcher.group(1) != null ? snippetMatcher.group(1) : snippetMatcher.group(2);
                if (snippet != null) {
                    snippet = snippet.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
                    if (snippet.length() > 150) snippet = snippet.substring(0, 150) + "...";
                    if (!snippet.isBlank()) sb.append("   ").append(snippet).append("\n");
                }
            }
            sb.append("   🔗 ").append(url).append("\n\n");
        }

        // Fallback: look for any <cite> tags (URLs) and surrounding <a> tags
        if (count == 0) {
            java.util.regex.Pattern citePattern = java.util.regex.Pattern.compile(
                    "<a[^>]*href=\"(https?://[^\"?]+)[^\"]*\"[^>]*>(.{10,100}?)</a>",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher citeMatcher = citePattern.matcher(html);
            while (citeMatcher.find() && count < 5) {
                String url = citeMatcher.group(1);
                String text = citeMatcher.group(2).replaceAll("<[^>]+>", "").trim();
                if (url.contains("bing.com") || url.contains("microsoft") || text.length() < 10) continue;
                count++;
                sb.append(count).append(". **").append(text).append("**\n");
                sb.append("   🔗 ").append(url).append("\n\n");
            }
        }

        if (count == 0) return null;
        return sb.toString();
    }

    private String formatResults(JSONArray list, String query) {
        StringBuilder sb = new StringBuilder();
        sb.append("📎 搜索结果：\n\n");
        int count = 0;
        for (int i = 0; i < list.size() && count < 5; i++) {
            JSONObject item = list.getJSONObject(i);
            if (item == null) continue;

            String title = item.getString("title");
            String url = item.getString("url");
            String desc = item.getString("abstract");
            if (desc == null) desc = item.getString("description");
            if (desc == null) desc = item.getString("snippet");

            if (title == null || title.isBlank()) continue;
            count++;
            sb.append(count).append(". **").append(title).append("**\n");
            if (desc != null && !desc.isBlank()) {
                if (desc.length() > 150) desc = desc.substring(0, 150) + "...";
                sb.append("   ").append(desc).append("\n");
            }
            if (url != null && !url.isBlank()) sb.append("   🔗 ").append(url).append("\n");
            sb.append("\n");
        }
        return sb.length() > 0 ? sb.toString() : null;
    }
}