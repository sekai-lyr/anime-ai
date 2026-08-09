package com.example.demo.ai;

import com.example.demo.anime.AnimeImageRecognitionService;
import com.example.demo.anime.AnimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final SpringAiChatService springAiChatService;
    private final ToolCallingService toolCallingService;
    private final AnimeImageRecognitionService animeImageRecognitionService;
    private final AnimeService animeService;
    private final AnimeEventService animeEventService;
    private final Map<String, String> conversationCities = new ConcurrentHashMap<>();

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String systemPrompt = request.get("systemPrompt");
        log.info("AnimeAI chat: {}", message != null && message.length() > 50 ? message.substring(0, 50) + "..." : message);
        Map<String, Object> response = new HashMap<>();
        try {
            if (isRecentAnimeMovieQuestion(message)) {
                String movies = animeService.getRecentAnimeMoviesAniList(15);
                if (movies != null && !movies.isBlank()) {
                    response.put("success", true);
                    response.put("content", movies);
                    return ResponseEntity.ok(response);
                }
            }
            ToolCallResponse toolResponse = springAiChatService.chatWithTools(
                    message,
                    systemPrompt == null || systemPrompt.isBlank() ? null : systemPrompt,
                    null,
                    request.get("conversationId"));
            String result = toolResponse.getText();
            result = sanitizeUnannouncedEventAnswer(result, toolResponse);
            response.put("success", true);
            response.put("content", result);
        } catch (Exception e) {
            log.error("AI chat failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    private boolean isRecentAnimeMovieQuestion(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(Locale.ROOT);
        boolean movie = normalized.contains("动漫电影") || normalized.contains("动画电影")
                || normalized.contains("anime movie") || normalized.contains("剧场版");
        boolean recent = normalized.contains("最近") || normalized.contains("近期")
                || normalized.contains("最新") || normalized.contains("今年")
                || normalized.contains("上映") || normalized.contains("档期");
        return movie && recent;
    }

    private boolean isAnimeEventQuestion(String message) {
        if (message == null) return false;
        String normalized = message.toLowerCase(Locale.ROOT);
        boolean event = normalized.contains("漫展") || normalized.contains("同人展")
                || normalized.contains("动漫节") || normalized.contains("comicup");
        boolean schedule = normalized.contains("最近") || normalized.contains("后面")
                || normalized.contains("接下来") || normalized.contains("日期")
                || normalized.contains("什么时候") || normalized.contains("举行");
        boolean preference = normalized.contains("纯漫展") || normalized.contains("二次元浓度")
                || normalized.contains("不想看") || normalized.contains("排除二游") || normalized.contains("同人向");
        return event && (schedule || preference);
    }

    private Optional<String> extractCity(String message) {
        if (message == null) return Optional.empty();
        Matcher matcher = Pattern.compile("(?:我在|位于|城市是|地点在)([\\p{IsHan}]{2,8}?)(?:市|，|,|。|\\s|最近|后面|接下来)").matcher(message);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private String searchAnimeEvents(String message, String conversationId) {
        Optional<String> explicitCity = extractCity(message);
        if (explicitCity.isPresent() && conversationId != null && !conversationId.isBlank()) {
            conversationCities.put(conversationId, explicitCity.get());
        }
        String city = explicitCity.orElseGet(() -> conversationId == null ? "杭州"
                : conversationCities.getOrDefault(conversationId, "杭州"));
        boolean pureAnime = message != null && (message.contains("纯漫展") || message.contains("二次元浓度")
                || message.contains("不想看") || message.contains("排除二游") || message.contains("同人向"));
        return animeEventService.searchIntelligence(city, pureAnime ? message + " 排除二游" : message, 8);
    }

    @PostMapping("/chat-with-tools")
    public ResponseEntity<Map<String, Object>> chatWithTools(@RequestBody Map<String, Object> request) {
        String message = (String) request.get("message");
        String systemPrompt = (String) request.get("systemPrompt");
        String conversationId = (String) request.get("conversationId");
        @SuppressWarnings("unchecked")
        List<String> allowedTools = (List<String>) request.get("allowedTools");
        log.info("AnimeAI tool chat: tools={}", allowedTools);
        Map<String, Object> response = new HashMap<>();
        try {
            if (isRecentAnimeMovieQuestion(message)) {
                String movies = animeService.getRecentAnimeMoviesAniList(15);
                if (movies != null && !movies.isBlank()) {
                    response.put("success", true);
                    response.put("content", movies);
                    response.put("toolsUsed", 1);
                    response.put("toolsSucceeded", 1);
                    return ResponseEntity.ok(response);
                }
            }
            List<String> validatedTools = toolCallingService.validateToolNames(allowedTools);
            Set<String> allowedToolSet = isAnimeEventQuestion(message)
                    ? Set.of("searchAnimeEventsChina")
                    : (validatedTools.isEmpty() ? null : new HashSet<>(validatedTools));
            ToolCallResponse toolResponse = springAiChatService.chatWithTools(message, systemPrompt, allowedToolSet, conversationId);
            response.put("success", true);
            response.put("content", sanitizeUnannouncedEventAnswer(toolResponse.getText(), toolResponse));
            response.put("traceId", toolResponse.getTraceId());
            response.put("totalIterations", toolResponse.getTotalIterations());
            response.put("totalTokens", toolResponse.getTotalTokens());
            if (toolResponse.hasToolCalls()) {
                response.put("toolCallHistory", toolResponse.getToolCallHistory().stream()
                        .map(this::convertToolCallResult).collect(Collectors.toList()));
            }
            if (toolResponse.hasGeneratedFiles())
                response.put("generatedFiles", toolResponse.getGeneratedFiles());
            if (toolResponse.getToolCallHistory() != null) {
                long successCount = toolResponse.getToolCallHistory().stream()
                        .filter(ToolCallResult::isSuccess).count();
                response.put("toolsUsed", toolResponse.getToolCallHistory().size());
                response.put("toolsSucceeded", successCount);
            }
        } catch (Exception e) {
            log.error("AI tool chat failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/analyze-image")
    public ResponseEntity<Map<String, Object>> analyzeImage(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            String image = request.get("image");
            if (image == null || image.isBlank()) {
                throw new IllegalArgumentException("请选择图片");
            }
            String imageUrl = image.startsWith("data:") ? image : "data:image/jpeg;base64," + image;
            log.info("Anime image recognition: dataLength={}", image.length());
            response.put("success", true);
            response.put("content", animeImageRecognitionService.recognize(imageUrl));
        } catch (Exception e) {
            log.error("Anime image analysis failed", e);
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tools/registered")
    public ResponseEntity<Map<String, Object>> getRegisteredTools() {
        Map<String, Object> response = new HashMap<>();
        Set<String> tools = toolCallingService.getRegisteredToolNames();
        response.put("success", true);
        response.put("data", tools);
        response.put("total", tools.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/tools/schema")
    public ResponseEntity<Map<String, Object>> getToolsSchema() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", toolCallingService.buildToolsSchema());
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> convertToolCallResult(ToolCallResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("toolName", result.getToolName());
        map.put("success", result.isSuccess());
        map.put("duration", result.getDurationMs() + "ms");
        if (result.isSuccess()) {
            String out = result.getResult();
            map.put("output", out != null && out.length() > 500 ? out.substring(0, 500) + "..." : out);
        } else {
            map.put("error", result.getErrorMessage());
        }
        return map;
    }

    private String sanitizeUnannouncedEventAnswer(String answer, ToolCallResponse toolResponse) {
        if (answer == null || toolResponse == null || toolResponse.getToolCallHistory() == null) return answer;
        boolean unannounced = toolResponse.getToolCallHistory().stream()
                .filter(result -> "searchAnimeEventsChina".equals(result.getToolName()))
                .map(ToolCallResult::getResult).filter(Objects::nonNull)
                .anyMatch(result -> result.contains("尚未官宣"));
        if (!unannounced) return answer;
        return answer.lines()
                .filter(line -> !line.contains("大概率") && !line.contains("按照往届")
                        && !line.contains("一年一届") && !line.contains("半年一届")
                        && !line.contains("预计在") && !line.contains("可能在")
                        && !line.contains("如果延续") && !line.contains("推测")
                        && !line.contains("一旦官方发布") && !line.contains("官宣后通知")
                        && !(line.contains("设置") && line.contains("活动提醒") && line.contains("官方"))
                        && !line.matches(".*(?:明年|后年|下一年).*(?:春|夏|秋|冬|月|届).*")
                        && !line.matches(".*(?:第八届|下一届).*(?:202[0-9]年|春|夏|秋|冬).*")
                        && !(line.contains("2025年") && line.contains("梦乡")))
                .collect(Collectors.joining("\n"));
    }
}
