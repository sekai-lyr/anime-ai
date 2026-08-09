
package com.example.demo.ai;

import com.alibaba.fastjson2.JSONObject;
import com.example.demo.agent.tools.*;
import com.example.demo.anime.AnimeService;
import com.example.demo.movie.ReminderService;
import com.example.demo.service.AmapService;
import com.example.demo.weather.model.WeatherResponse;
import com.example.demo.weather.service.WeatherService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
public class SpringAiTools {

    private static volatile boolean jikanAvailable = true;

    private final WeatherService weatherService;
    private final WebSearchTool webSearchTool;
    private final ImageAnalysisTool imageAnalysisTool;
    private final AnimeService animeService;
    private final AmapService amapService;
    private final ReminderService reminderService;
private final SpringAiChatService chatService;

    public SpringAiTools(WeatherService weatherService,
                         WebSearchTool webSearchTool,
                         ImageAnalysisTool imageAnalysisTool,
                         AnimeService animeService,
                         AmapService amapService,
                         @Lazy SpringAiChatService chatService,
                         ReminderService reminderService) {
        this.weatherService = weatherService;
        this.webSearchTool = webSearchTool;
        this.imageAnalysisTool = imageAnalysisTool;
        this.animeService = animeService;
        this.amapService = amapService;
        this.chatService = chatService;
        this.reminderService = reminderService;
    }

    @PostConstruct
    public void init() { log.info("AnimeAI SpringAiTools initialized"); }

    private String jikanCall(String toolName, java.util.function.Supplier<String> call) {
        if (!jikanAvailable) return "[Jikan unavailable, use webSearch]";
        try {
            String result = call.get();
            if (result == null) { jikanAvailable = false; return "[Jikan unavailable, use webSearch]"; }
            return result;
        } catch (Exception e) { jikanAvailable = false; return "[Jikan unavailable, use webSearch]"; }
    }

    @Tool(name = "getCurrentTime", description = "Get current system time and date")
    public String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE"));
    }

    @Tool(name = "getWeather", description = "Query real-time weather for a city")
    public WeatherResponse getWeather(@ToolParam(description = "City name") String city) {
        return weatherService.getWeatherByCity(city);
    }

    @Tool(name = "webSearch", description = "Search the web for real-time info. PRIMARY tool for anime news, releases, announcements.")
    public String webSearch(@ToolParam(description = "Search keywords") String query) {
        JSONObject params = new JSONObject();
        params.put("query", query);
        ToolResult<String> result = webSearchTool.execute(params);
        return result.isSuccess() ? result.getData() : "Search failed: " + result.getMessage();
    }

    @Tool(name = "searchAnime", description = "Search anime by title. AniList first (real scores), Jikan fallback.")
    public String searchAnime(
            @ToolParam(description = "Anime title") String query,
            @ToolParam(description = "Type filter: tv/movie/ova") String type,
            @ToolParam(description = "Page, default 1") int page,
            @ToolParam(description = "Max 1-25, default 5") int limit) {
        int p = page < 1 ? 1 : page;
        int l = (limit < 1 || limit > 25) ? 5 : limit;
        if ("movie".equalsIgnoreCase(type)) {
            String mr = animeService.searchAnimeMovie(query, l);
            if (mr != null) return mr;
        }
        String aniResult = animeService.searchAnimeAniList(query, l);
        if (aniResult != null) return aniResult;
        return jikanCall("searchAnime", () -> animeService.searchAnime(query, type, p, l));
    }

    @Tool(name = "searchAnimeMovie", description = "Search for anime MOVIES specifically. Returns title, score, release date, duration.")
    public String searchAnimeMovie(
            @ToolParam(description = "Movie title to search") String query,
            @ToolParam(description = "Max results 1-10, default 5") int limit) {
        if (limit < 1 || limit > 10) limit = 5;
        String result = animeService.searchAnimeMovie(query, limit);
        return result != null ? result : "[No movie found. Try searchAnime with type=movie or use webSearch.]";
    }

    @Tool(name = "getUpcomingMovies", description = "Get upcoming anime MOVIES not yet released. Shows release dates.")
    public String getUpcomingMovies(
            @ToolParam(description = "Max results 1-10, default 5") int limit) {
        if (limit < 1 || limit > 10) limit = 5;
        return animeService.getUpcomingMoviesAniList(limit);
    }

    @Tool(name = "getRecentAnimeMovies", description = "PRIMARY tool for recent anime movie questions. Returns recently released and upcoming movies with dates and AniList URLs.")
    public String getRecentAnimeMovies(
            @ToolParam(description = "Max results 1-20, default 12") int limit) {
        int safeLimit = limit < 1 || limit > 20 ? 12 : limit;
        return animeService.getRecentAnimeMoviesAniList(safeLimit);
    }

    @Tool(name = "searchAnimeNews", description = "Search for anime NEWS with anime-specific web search. Best for release dates and announcements.")
    public String searchAnimeNews(
            @ToolParam(description = "News topic, e.g. 2026 anime movie release dates") String topic) {
        String r = webSearch("site:animenewsnetwork.com OR site:crunchyroll.com " + topic + " anime");
        if (r != null && !r.contains("Search failed")) return r;
        return webSearch(topic + " anime news 2026");
    }

    @Tool(name = "getAnimeDetail", description = "Get full anime detail by MAL ID: score, episodes, studio, genres.")
    public String getAnimeDetail(@ToolParam(description = "MAL anime ID") int animeId) {
        return jikanCall("getAnimeDetail", () -> animeService.getAnimeById(animeId));
    }

    @Tool(name = "getCurrentSeasonAnime", description = "Currently airing seasonal anime. AniList first, Jikan fallback.")
    public String getCurrentSeasonAnime() {
        java.time.LocalDate now = java.time.LocalDate.now();
        int y = now.getYear(); int m = now.getMonthValue();
        String s = m <= 3 ? "winter" : m <= 6 ? "spring" : m <= 9 ? "summer" : "fall";
        String result = animeService.getSeasonalAniList(s, y);
        if (result != null) return result;
        return jikanCall("getCurrentSeasonAnime", () -> animeService.getCurrentSeasonAnime());
    }

    @Tool(name = "getSeasonAnime", description = "Anime for a year/season. AniList first, Jikan fallback.")
    public String getSeasonAnime(
            @ToolParam(description = "Year, e.g. 2026") int year,
            @ToolParam(description = "Season: winter/spring/summer/fall") String season) {
        String result = animeService.getSeasonalAniList(season, year);
        if (result != null) return result;
        return jikanCall("getSeasonAnime", () -> animeService.getSeasonAnime(year, season));
    }

    @Tool(name = "getUpcomingAnime", description = "Upcoming anime about to air. AniList first.")
    public String getUpcomingAnime() {
        String result = animeService.getUpcomingAnimeAniList(10);
        if (result != null) return result;
        return jikanCall("getUpcomingAnime", () -> animeService.getUpcomingAnime());
    }

    @Tool(name = "getTopAnime", description = "Top anime by rating or popularity. AniList first.")
    public String getTopAnime(
            @ToolParam(description = "Filter: rating/popular. Default: rating") String filter,
            @ToolParam(description = "Max 1-25, default 10") int limit) {
        int l2 = (limit < 1 || limit > 25) ? 10 : limit;
        if ("popular".equalsIgnoreCase(filter) || "bypopularity".equalsIgnoreCase(filter)) {
            String r = animeService.getPopularAnimeAniList(l2);
            if (r != null) return r;
        }
        String r = animeService.getTopAnimeAniList(l2);
        if (r != null) return r;
        return jikanCall("getTopAnime", () -> animeService.getTopAnime(filter, l2));
    }

    @Tool(name = "searchAnimeCharacter", description = "Search anime characters. Jikan first, AI knowledge fallback.")
    public String searchAnimeCharacter(
            @ToolParam(description = "Character name") String query,
            @ToolParam(description = "Page, default 1") int page,
            @ToolParam(description = "Max 1-25, default 5") int limit) {
        if (page < 1) page = 1; if (limit < 1 || limit > 25) limit = 5;
        String r = animeService.searchCharacter(query, page, limit);
        if (r != null) return "[Jikan] " + r;
        try { return "[AI Knowledge] " + chatService.chat(query, "Describe anime character: " + query + ". Include name, anime, role, personality. Under 200 words."); }
        catch (Exception e) { return "Character not found."; }
    }

    @Tool(name = "getAnimeNews", description = "Latest anime releases from MAL.")
    public String getAnimeNews(
            @ToolParam(description = "Page, default 1") int page,
            @ToolParam(description = "Max 1-25, default 10") int limit) {
        int p4 = page < 1 ? 1 : page; int l4 = (limit < 1 || limit > 25) ? 10 : limit;
        return jikanCall("getAnimeNews", () -> animeService.getAnimeNews(p4, l4));
    }

    @Tool(name = "searchNearbyTheater", description = "Search nearby movie theaters with Amap navigation links.")
    public String searchNearbyTheater(
            @ToolParam(description = "City/location") String location,
            @ToolParam(description = "Keyword, e.g. cinema") String keywords) {
        try {
            AmapService.GeocodeResult geo = amapService.geocode(location);
            String lngLat = geo.getLng() + "," + geo.getLat();
            List<AmapService.PoiItem> pois = amapService.searchNearbyPois(lngLat,
                    keywords != null && !keywords.isBlank() ? keywords : "cinema", 5000, 10);
            if (pois.isEmpty()) return "No theaters found near " + location;
            StringBuilder sb = new StringBuilder();
            sb.append("=== Nearby Theaters ===\nLocation: ").append(geo.getFormattedAddress()).append("\n\n");
            int count = Math.min(pois.size(), 5);
            for (int i = 0; i < count; i++) {
                AmapService.PoiItem poi = pois.get(i);
                sb.append("[").append(i + 1).append("] ").append(poi.getName()).append("\n");
                sb.append("    Address: ").append(poi.getAddress()).append("\n");
                if (poi.getTel() != null && !poi.getTel().isBlank()) sb.append("    Phone: ").append(poi.getTel()).append("\n");
                if (poi.getDistance() != null) sb.append("    Distance: ").append(poi.getDistance()).append("m\n");
                sb.append("    [Navigation Link] ").append(amapService.buildWebNavigationUrl(geo.getLng(), geo.getLat(), "Your Location", poi.getLng(), poi.getLat(), poi.getName())).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) { return "Theater search error: " + e.getMessage(); }
    }

    @Tool(name = "getLocationInfo", description = "Geocode an address to coordinates.")
    public String getLocationInfo(@ToolParam(description = "Address") String address) {
        try {
            AmapService.GeocodeResult geo = amapService.geocode(address);
            return String.format("Location: %s\nCity: %s\nCoordinates: %s,%s", geo.getFormattedAddress(), geo.getCity(), geo.getLng(), geo.getLat());
        } catch (Exception e) { return "Location lookup failed: " + e.getMessage(); }
    }

    @Tool(name = "createAnimeReminder", description = "Create an in-app anime reminder that will be delivered inside the current chat at the specified local date and time.")
    public String createAnimeReminder(
            @ToolParam(description = "Current conversation ID from the system context") String conversationId,
            @ToolParam(description = "Reminder title") String title,
            @ToolParam(description = "Date in YYYY-MM-DD") String date,
            @ToolParam(description = "Time in HH:mm") String time) {
        try {
            if (conversationId == null || conversationId.isBlank()) return "无法创建提醒：缺少当前会话 ID。";
            LocalDateTime remindTime = LocalDateTime.parse(date + " " + time,
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            reminderService.createReminder(conversationId, title, remindTime);
            return "提醒已创建：" + title + "｜" + remindTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    + "。到点后会在当前聊天中发送消息。";
        } catch (Exception error) {
            return "提醒创建失败：" + error.getMessage();
        }
    }

    @Tool(name = "getDomesticMovieTicketLinks", description = "Get Chinese movie ticket purchase links for a selected movie.")
    public String getDomesticMovieTicketLinks(@ToolParam(description = "Movie title") String movieTitle) {
        return animeService.getDomesticMovieTicketLink(movieTitle)
                + "\n告诉我城市、区县或商圈后，我还能搜索附近影院并生成高德导航链接。";
    }

    @Tool(name = "getAnimeAiringSchedule", description = "Get the real-time anime episode airing schedule for a date.")
    public String getAnimeAiringSchedule(
            @ToolParam(description = "Date in YYYY-MM-DD; leave blank for today") String date,
            @ToolParam(description = "Max results 1-30, default 15") int limit) {
        java.time.LocalDate target = date == null || date.isBlank()
                ? java.time.LocalDate.now() : java.time.LocalDate.parse(date);
        String result = animeService.getAnimeAiringSchedule(target, limit < 1 || limit > 30 ? 15 : limit);
        return result != null ? result + "\n选定作品后可以让我生成更新提醒。" : "播出日历暂时不可用，请稍后重试。";
    }

    @Tool(name = "getAnimeWatchLinks", description = "Find official streaming links for an anime.")
    public String getAnimeWatchLinks(@ToolParam(description = "Anime title") String animeTitle) {
        String result = animeService.getAnimeWatchLinks(animeTitle);
        return result != null ? result : "没有找到播放入口。请告诉我国家或地区，我将搜索当地正版平台。";
    }

    @Tool(name = "getAnimeRelations", description = "Get sequel, prequel and side-story relations.")
    public String getAnimeRelations(@ToolParam(description = "Anime title") String animeTitle) {
        String result = animeService.getAnimeRelations(animeTitle);
        return result != null ? result : "没有找到作品关系。";
    }

    @Tool(name = "getAnimeRecommendations", description = "Get AniList recommendations for an anime.")
    public String getAnimeRecommendations(
            @ToolParam(description = "Anime title") String animeTitle,
            @ToolParam(description = "Max results 1-15, default 8") int limit) {
        String result = animeService.getAnimeRecommendations(animeTitle, limit < 1 || limit > 15 ? 8 : limit);
        return result != null ? result : "暂时没有找到相关推荐。";
    }
}
