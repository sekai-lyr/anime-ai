
package com.example.demo.ai;

import com.example.demo.chat.VectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class SpringAiChatService {

    private final ChatModel chatModel;
    private final VectorStoreService vectorStoreService;
    private final ToolCallingService toolCallingService;

    private static final String SYSTEM_PROMPT = """
        # Role
        You are AnimeAI, a passionate anime assistant. You have access to AniList API for real anime data, plus weather, maps, and web search tools.

        # CRITICAL: Which Tool to Use
        Match user intent to the EXACT tool:

        User wants to → Use this tool:
        - Search an anime → searchAnime
        - Find a MOVIE → searchAnimeMovie
        - Upcoming movies → getUpcomingMovies
        - This season airing → getCurrentSeasonAnime
        - Specific year/season → getSeasonAnime
        - Upcoming anime (TV) → getUpcomingAnime
        - Top/best/rankings → getTopAnime
        - Character info → searchAnimeCharacter
        - News/release dates → searchAnimeNews
        - Weather → getWeather
        - Nearby theaters → searchNearbyTheater
        - Current time → getCurrentTime

        # Rules
        1. Call 1-3 tools MAX per request. Never more.
        2. If a tool returns real data, STOP and present it.
        3. If AniList returns empty, try ONE webSearch as fallback.
        4. For movie questions: searchAnimeMovie FIRST.
        5. For upcoming: getUpcomingMovies FIRST, then getUpcomingAnime.
        6. Respond in the user's language. Be concise but enthusiastic.
        """;

    private static final String REALTIME_ANIME_RULES = """

        # Mandatory real-time anime rules
        - Recent/latest anime movies: call getRecentAnimeMovies first.
        - Upcoming anime movies: call getUpcomingMovies first.
        - A named anime movie: call searchAnimeMovie first.
        - News and release announcements: call searchAnimeNews.
        - Questions containing 最近、最新、今年、上映、档期、新闻 or a current/future year MUST use tools, never model memory.
        - Include dates, release status and clickable source URLs. Distinguish Japan release from mainland China release.
        - Use up to three tools and provide a comprehensive answer for list questions.
        - Movie journey: discover movie -> ask for city/district if missing -> searchNearbyTheater -> provide Amap navigation -> getDomesticMovieTicketLinks -> optionally createMovieReminder.
        - Never invent a user's location. If MySQL history and RAG do not contain it, explicitly ask for city/district or business area at the end.
        - Airing journey: get current date -> getAnimeAiringSchedule -> user selects anime -> createMovieReminder.
        - Watching journey: identify title -> getAnimeWatchLinks -> ask country/region when rights availability is unclear.
        - Series journey: getAnimeRelations -> organize viewing order -> getAnimeWatchLinks for the selected entry.
        - Recommendation journey: getAnimeRecommendations -> ask genre/mood preference -> searchAnime for the chosen title.
        - Restaurant journey: ask location if missing -> recommendAnimeRestaurants once -> user selects restaurant -> provide navigation and phone/queue guidance.
        - Event journey: searchAnimeEventsChina once -> user selects event -> planAnimeEventTrip -> nearby restaurant/navigation -> ticket source.
        - For a named event, preserve the exact event name in tool parameters. If no next edition is confirmed, say only that it is not announced; never guess cadence, month, date or venue.
        - Do not offer automatic announcement monitoring unless a real monitoring tool was executed successfully.
        - When organizer account data includes recently checked posts, state that those posts were checked. Do not merely tell the user to follow or search the account.
        - Merchandise journey: recommendAnimeMerchandise once -> open local shop product -> order through the existing shop flow.
        - Outdoor event journey: planAnimeEventTrip combines weather and venue location; never invent forecast or venue data.
        - Cross-media journey: recommendGamesFromAnime once -> ask platform and preferred gameplay before narrowing results.
        - Do not repeat the same tool in one answer unless the first call failed.
        """;

    @Autowired
    public SpringAiChatService(ChatModel chatModel, VectorStoreService vectorStoreService, 
                               ToolCallingService toolCallingService) {
        this.chatModel = chatModel;
        this.vectorStoreService = vectorStoreService;
        this.toolCallingService = toolCallingService;
        log.info("AnimeAI SpringAiChatService initialized");
    }

    public String chat(String userMessage) {
        return chat(userMessage, SYSTEM_PROMPT, null);
    }

    public String chat(String userMessage, String systemPrompt) {
        return chat(userMessage, systemPrompt, null);
    }

    public String chat(String userMessage, String systemPrompt, String conversationId) {
        log.info("AnimeAI chat, msg len: {}", userMessage.length());
        List<Message> messages = new ArrayList<>();
        String effectivePrompt = (systemPrompt != null && !systemPrompt.isBlank() ? systemPrompt : SYSTEM_PROMPT) + REALTIME_ANIME_RULES;
        messages.add(new SystemMessage(effectivePrompt));
        messages.add(new UserMessage(userMessage));
        Prompt prompt = new Prompt(messages);
        try {
            ChatResponse response = chatModel.call(prompt);
            String content = response.getResult().getOutput().toString();
            log.info("AnimeAI chat done, response len: {}", content.length());
            return content;
        } catch (Exception e) {
            log.error("AnimeAI chat failed", e);
            throw new RuntimeException("AI service error: " + e.getMessage(), e);
        }
    }

    public String chatWithTools(String userMessage) {
        return chatWithTools(userMessage, SYSTEM_PROMPT, null).getText();
    }

    public String chatWithTools(String userMessage, String systemPrompt) {
        return chatWithTools(userMessage, systemPrompt, null).getText();
    }

    public ToolCallResponse chatWithToolsFull(String userMessage) {
        return chatWithTools(userMessage, SYSTEM_PROMPT, null);
    }

    public ToolCallResponse chatWithTools(String userMessage, String systemPrompt,
                                           Set<String> allowedToolNames) {
        return chatWithTools(userMessage, systemPrompt, allowedToolNames, null);
    }

    public ToolCallResponse chatWithTools(String userMessage, String systemPrompt,
                                           Set<String> allowedToolNames, String conversationId) {
        log.info("AnimeAI tool chat, msg: len={}, tools={}, convId={}", userMessage.length(), allowedToolNames, conversationId);
        String effectivePrompt = (systemPrompt != null && !systemPrompt.isBlank() ? systemPrompt : SYSTEM_PROMPT) + REALTIME_ANIME_RULES;
        return toolCallingService.chatWithTools(effectivePrompt, userMessage, allowedToolNames, conversationId);
    }

    public Flux<String> chatWithToolsStream(String userMessage) {
        return Flux.just("Stream mode not yet implemented.");
    }

    public String chatWithTemplate(String template, Map<String, Object> variables) {
        PromptTemplate promptTemplate = new PromptTemplate(template);
        Prompt prompt = promptTemplate.create(variables);
        try {
            return chatModel.call(prompt).getResult().getOutput().toString();
        } catch (Exception e) {
            log.error("Template chat failed", e);
            throw new RuntimeException("AI template error: " + e.getMessage(), e);
        }
    }
}
