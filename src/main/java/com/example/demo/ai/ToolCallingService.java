package com.example.demo.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.example.demo.chat.ChatMemoryService;
import com.example.demo.chat.ChatMessage;
import com.example.demo.chat.LlmService;
import com.example.demo.chat.VectorStoreService;
import com.example.demo.weather.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ToolCallingService {

    private static final int MAX_ITERATIONS = 5;

    private final LlmService llmService;
    private final SpringAiTools springAiTools;
    private final AnimeLifestyleTools animeLifestyleTools;
    private final WeatherService weatherService;
    private final ChatMemoryService chatMemoryService;
    private final VectorStoreService vectorStoreService;
    private final Map<String, ToolInfo> toolRegistry = new LinkedHashMap<>();
    private final ExecutorService toolExecutor;

    @Autowired
    public ToolCallingService(LlmService llmService, SpringAiTools springAiTools,
                              AnimeLifestyleTools animeLifestyleTools,
                              WeatherService weatherService,
                              ChatMemoryService chatMemoryService,
                              VectorStoreService vectorStoreService) {
        this.llmService = llmService;
        this.springAiTools = springAiTools;
        this.animeLifestyleTools = animeLifestyleTools;
        this.weatherService = weatherService;
        this.chatMemoryService = chatMemoryService;
        this.vectorStoreService = vectorStoreService;
        this.toolExecutor = Executors.newFixedThreadPool(
                Math.min(Runtime.getRuntime().availableProcessors(), 4),
                r -> { Thread t = new Thread(r, "tool-executor"); t.setDaemon(true); return t; });
        registerTools(springAiTools);
        registerTools(animeLifestyleTools);
        registerTools(weatherService);
        log.info("ToolCallingService initialized with {} tools + memory + RAG", toolRegistry.size());
    }

    // ============ Public API ============

    public ToolCallResponse chatWithTools(String systemPrompt, String userMessage) {
        return chatWithTools(systemPrompt, userMessage, null, null);
    }

    public ToolCallResponse chatWithTools(String systemPrompt, String userMessage,
                                           Set<String> allowedToolNames) {
        return chatWithTools(systemPrompt, userMessage, allowedToolNames, null);
    }

    /** Full-featured: with conversation memory + RAG context */
    public ToolCallResponse chatWithTools(String systemPrompt, String userMessage,
                                           Set<String> allowedToolNames, String conversationId) {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        log.info("[Trace:{}] Tool chat started, convId={}, msgLen={}",
                traceId, conversationId, userMessage.length());

        // Set conversation context for tools
        if (conversationId != null && !conversationId.isBlank()) {
            UserContextHolder.setConversationId(conversationId);
        }

        JSONArray messages = new JSONArray();

        // 1. System prompt (with optional RAG)
        String effectivePrompt = systemPrompt;
        if (conversationId != null && !conversationId.isBlank()) {
            effectivePrompt += "\n\n# Runtime Context\nCurrent conversationId: " + conversationId
                    + "\nWhen calling createAnimeReminder, pass this exact conversationId.";
        }
        if (conversationId != null && !conversationId.isBlank() && vectorStoreService != null) {
            try {
                List<String> ragResults = vectorStoreService.searchSimilar(userMessage, conversationId);
                if (ragResults != null && !ragResults.isEmpty()) {
                    StringBuilder ragCtx = new StringBuilder("\n\n# Relevant Knowledge (RAG)\n");
                    for (int i = 0; i < Math.min(ragResults.size(), 3); i++) {
                        ragCtx.append("[").append(i + 1).append("] ").append(ragResults.get(i)).append("\n");
                    }
                    effectivePrompt += ragCtx.toString();
                }
            } catch (Exception e) { log.debug("RAG skipped: {}", e.getMessage()); }
        }

        JSONObject sysMsg = new JSONObject();
        sysMsg.put("role", "system");
        sysMsg.put("content", effectivePrompt != null ? effectivePrompt : "");
        messages.add(sysMsg);

        // 2. Conversation history from MySQL (rolling summary)
        if (conversationId != null && !conversationId.isBlank() && chatMemoryService != null) {
            try {
                List<ChatMessage> history = chatMemoryService.buildPromptMessages(
                        conversationId, null, userMessage);
                // Skip first (system) and last (current user), add middle ones
                for (int i = 0; i < history.size(); i++) {
                    ChatMessage msg = history.get(i);
                    if (msg.getRole().equals("system")) continue;
                    JSONObject histMsg = new JSONObject();
                    histMsg.put("role", msg.getRole());
                    histMsg.put("content", msg.getContent());
                    messages.add(histMsg);
                }
            } catch (Exception e) { log.debug("History load skipped: {}", e.getMessage()); }
        } else {
            // No history: just add current user message
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.add(userMsg);
        }

        // 3. Execute tool loop
        try {
            ToolCallResponse response = executeToolLoop(messages, allowedToolNames, traceId);

            // 4. Save to memory (async)
            if (conversationId != null && !conversationId.isBlank() && chatMemoryService != null && response.getText() != null) {
                try {
                    chatMemoryService.saveMessagePair(conversationId, userMessage, response.getText());
                    // Also save to vector store for RAG
                    if (vectorStoreService != null) {
                        vectorStoreService.saveMessage(conversationId, userMessage, response.getText());
                    }
                } catch (Exception e) { log.warn("Failed to save memory: {}", e.getMessage()); }
            }
            return response;
        } finally {
            UserContextHolder.clear();
        }
    }

    // ============ Tool Registry ============

    public Set<String> getRegisteredToolNames() { return new HashSet<>(toolRegistry.keySet()); }

    public JSONArray buildToolsSchema() { return buildToolsSchema(null); }

    public JSONArray buildToolsSchema(Set<String> allowedToolNames) {
        JSONArray tools = new JSONArray();
        for (Map.Entry<String, ToolInfo> entry : toolRegistry.entrySet()) {
            if (allowedToolNames != null && !allowedToolNames.isEmpty()
                    && !allowedToolNames.contains(entry.getKey())) continue;
            ToolInfo ti = entry.getValue();
            JSONObject tool = new JSONObject();
            tool.put("type", "function");
            JSONObject fn = new JSONObject();
            fn.put("name", entry.getKey());
            fn.put("description", ti.annotation.description().isEmpty() ? entry.getKey() : ti.annotation.description());
            fn.put("parameters", buildParametersSchema(ti.method));
            tool.put("function", fn);
            tools.add(tool);
        }
        return tools;
    }

    public List<String> validateToolNames(List<String> requestedTools) {
        if (requestedTools == null || requestedTools.isEmpty()) return new ArrayList<>(toolRegistry.keySet());
        return requestedTools.stream().filter(toolRegistry::containsKey).collect(Collectors.toList());
    }

    // ============ Internals (unchanged) ============

    private void registerTools(Object obj) {
        for (Method m : obj.getClass().getDeclaredMethods()) {
            Tool ann = m.getAnnotation(Tool.class);
            if (ann != null) {
                String name = ann.name().isEmpty() ? m.getName() : ann.name();
                toolRegistry.put(name, new ToolInfo(obj, m, ann));
            }
        }
    }

    private JSONObject buildParametersSchema(Method method) {
        JSONObject p = new JSONObject(); p.put("type", "object");
        JSONObject props = new JSONObject(); JSONArray req = new JSONArray();
        for (Parameter param : method.getParameters()) {
            JSONObject ps = new JSONObject(); ps.put("type", getJsonType(param.getType()));
            ToolParam pa = param.getAnnotation(ToolParam.class);
            ps.put("description", pa != null && !pa.description().isEmpty() ? pa.description() : "Param: " + param.getName());
            props.put(param.getName(), ps);
            if (pa == null || pa.required()) req.add(param.getName());
        }
        p.put("properties", props);
        if (!req.isEmpty()) p.put("required", req);
        return p;
    }

    private String getJsonType(Class<?> t) {
        if (t == String.class) return "string";
        if (t == int.class || t == Integer.class || t == long.class || t == Long.class) return "integer";
        if (t == double.class || t == Double.class || t == float.class || t == Float.class) return "number";
        if (t == boolean.class || t == Boolean.class) return "boolean";
        if (t.isArray() || List.class.isAssignableFrom(t)) return "array";
        return "object";
    }

    private ToolCallResponse executeToolLoop(JSONArray messages, Set<String> allowedToolNames, String traceId) {
        JSONArray tools = buildToolsSchema(allowedToolNames);
        List<ToolCallResult> history = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        long tokens = 0;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            log.info("[Trace:{}] Iteration {}/{}", traceId, i + 1, MAX_ITERATIONS);
            try {
                JSONObject resp = llmService.chatWithTools(messages, tools);
                tokens += extractTokens(resp);
                if (hasToolCalls(resp)) {
                    JSONObject am = getAssistantMessage(resp);
                    if (am != null) messages.add(am);
                    List<ToolCallInfo> calls = parseToolCalls(resp);
                    Map<String, Future<ToolCallResult>> futures = new LinkedHashMap<>();
                    for (ToolCallInfo tc : calls) {
                        futures.put(tc.id, CompletableFuture.supplyAsync(() -> {
                            long start = System.currentTimeMillis();
                            try {
                                String r = executeTool(tc.toolName, tc.arguments);
                                long dur = System.currentTimeMillis() - start;
                                return ToolCallResult.success(traceId, tc.toolName, tc.arguments, r, dur);
                            } catch (Exception e) {
                                return ToolCallResult.error(traceId, tc.toolName, tc.arguments, e.getMessage(),
                                        System.currentTimeMillis() - start);
                            }
                        }, toolExecutor));
                    }
                    for (Map.Entry<String, Future<ToolCallResult>> e : futures.entrySet()) {
                        ToolCallResult cr = e.getValue().get(10, TimeUnit.SECONDS);
                        history.add(cr);
                        files.addAll(extractGeneratedFiles(cr.getResult()));
                        JSONObject tm = new JSONObject();
                        tm.put("role", "tool"); tm.put("tool_call_id", e.getKey());
                        tm.put("content", cr.getResult()); messages.add(tm);
                    }
                    if (allowedToolNames != null && allowedToolNames.size() == 1) {
                        tools = new JSONArray();
                    }
                } else {
                    String c = getTextContent(resp);
                    if (c != null && !c.isBlank()) text.append(c);
                    break;
                }
            } catch (Exception e) {
                log.error("[Trace:{}] Iteration {} failed: {}", traceId, i, e.getMessage());
                break;
            }
        }
        if (text.isEmpty()) {
            for (int index = history.size() - 1; index >= 0; index--) {
                ToolCallResult result = history.get(index);
                if (result.isSuccess() && result.getResult() != null && !result.getResult().isBlank()) {
                    text.append(result.getResult());
                    break;
                }
            }
        }
        return ToolCallResponse.builder().text(text.toString()).toolCallHistory(history)
                .generatedFiles(files).totalIterations(history.size()).totalTokens(tokens).traceId(traceId).build();
    }

    // ----- JSON parsing helpers -----
    private boolean hasToolCalls(JSONObject resp) {
        JSONArray choices = resp.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) return false;
        JSONObject msg = choices.getJSONObject(0).getJSONObject("message");
        if (msg == null) return false;
        JSONArray tc = msg.getJSONArray("tool_calls");
        return tc != null && !tc.isEmpty();
    }

    private JSONObject getAssistantMessage(JSONObject resp) {
        JSONArray choices = resp.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) return null;
        return choices.getJSONObject(0).getJSONObject("message");
    }

    private String getTextContent(JSONObject resp) {
        JSONArray choices = resp.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) return null;
        return choices.getJSONObject(0).getJSONObject("message").getString("content");
    }

    private long extractTokens(JSONObject resp) {
        try {
            JSONObject usage = resp.getJSONObject("usage");
            return usage != null ? usage.getLongValue("total_tokens") : 0;
        } catch (Exception e) { return 0; }
    }

    private List<ToolCallInfo> parseToolCalls(JSONObject resp) {
        List<ToolCallInfo> list = new ArrayList<>();
        JSONArray choices = resp.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) return list;
        JSONArray tc = choices.getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls");
        if (tc == null) return list;
        for (int i = 0; i < tc.size(); i++) {
            JSONObject t = tc.getJSONObject(i);
            JSONObject fn = t.getJSONObject("function");
            list.add(new ToolCallInfo(t.getString("id"), fn.getString("name"),
                    fn.getJSONObject("arguments") != null ? fn.getJSONObject("arguments") : new JSONObject()));
        }
        return list;
    }

    // ----- Tool execution -----
    private String executeTool(String name, JSONObject args) throws Exception {
        ToolInfo ti = toolRegistry.get(name);
        if (ti == null) throw new IllegalArgumentException("Unknown tool: " + name);
        Parameter[] params = ti.method.getParameters();
        Object[] argVals = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            String pn = params[i].getName();
            argVals[i] = args != null && args.containsKey(pn) ? convertArg(args.get(pn), params[i].getType()) : defaultVal(params[i].getType());
        }
        ti.method.setAccessible(true);
        return convertResult(ti.method.invoke(ti.target, argVals));
    }

    private Object convertArg(Object v, Class<?> t) {
        if (v == null) return defaultVal(t);
        if (t == String.class) return v.toString();
        if (t == int.class || t == Integer.class) return Integer.parseInt(v.toString());
        if (t == long.class || t == Long.class) return Long.parseLong(v.toString());
        if (t == double.class || t == Double.class) return Double.parseDouble(v.toString());
        if (t == boolean.class || t == Boolean.class) return Boolean.parseBoolean(v.toString());
        return v;
    }

    private Object defaultVal(Class<?> t) {
        if (t == String.class) return "";
        if (t == int.class || t == Integer.class) return 0;
        if (t == long.class || t == Long.class) return 0L;
        if (t == double.class || t == Double.class) return 0.0;
        if (t == boolean.class || t == Boolean.class) return false;
        return null;
    }

    private String convertResult(Object r) {
        if (r == null) return "";
        if (r instanceof String) return (String) r;
        return JSON.toJSONString(r);
    }

    private List<Path> extractGeneratedFiles(String r) {
        List<Path> fs = new ArrayList<>();
        if (r == null) return fs;
        try {
            if (r.startsWith("[IMAGE:") && r.contains("]")) fs.add(Path.of(r.substring(7, r.indexOf("]"))));
            if (r.startsWith("[AUDIO:") && r.contains("]")) fs.add(Path.of(r.substring(7, r.indexOf("]"))));
        } catch (Exception ignored) {}
        return fs;
    }

    public void shutdown() { toolExecutor.shutdown(); try { toolExecutor.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { toolExecutor.shutdownNow(); } }

    private static class ToolInfo { final Object target; final Method method; final Tool annotation;
        ToolInfo(Object t, Method m, Tool a) { this.target = t; this.method = m; this.annotation = a; } }
    private static class ToolCallInfo { final String id, toolName; final JSONObject arguments;
        ToolCallInfo(String i, String n, JSONObject a) { this.id = i; this.toolName = n; this.arguments = a; } }
}

