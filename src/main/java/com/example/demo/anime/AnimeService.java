package com.example.demo.anime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import com.example.demo.movie.MovieTicketService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;

@Slf4j
@Service
public class AnimeService {

    private final HttpClient httpClient;
    private final MovieTicketService movieTicketService;

    public AnimeService(MovieTicketService movieTicketService) {
        this.movieTicketService = movieTicketService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    // ============ searchAnime ============
    public String searchAnime(String query, String type, int page, int limit) {
        try {
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            String url = String.format("https://api.jikan.moe/v4/anime?q=%s&type=%s&page=%d&limit=%d&sfw=true",
                    encoded, type != null ? type : "", page, limit);
            String result = httpGet(url);
            JSONObject json = JSON.parseObject(result);
            JSONArray data = json.getJSONArray("data");
            if (data == null || data.isEmpty())
                return null;
            StringBuilder sb = new StringBuilder();
            sb.append("=== Anime Search Results ===\n\n");
            int count = Math.min(data.size(), limit);
            for (int i = 0; i < count; i++)
                sb.append(formatAnimeEntry(data.getJSONObject(i), i + 1));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    // ============ getAnimeDetail (with seiyuu) ============
    public String getAnimeById(int id) {
        try {
            String result = httpGet("https://api.jikan.moe/v4/anime/" + id + "/full");
            JSONObject data = JSON.parseObject(result).getJSONObject("data");
            if (data == null) return null;
            return formatAnimeDetail(data);
        } catch (Exception e) { return null; }
    }

    // ============ getCurrentSeasonAnime ============
    public String getCurrentSeasonAnime() {
        try {
            LocalDate now = LocalDate.now();
            int year = now.getYear();
            int m = now.getMonthValue();
            String season = m <= 3 ? "winter" : m <= 6 ? "spring" : m <= 9 ? "summer" : "fall";
            return getSeasonalAnimeInternal(season, year, "=== Current Season Anime ===");
        } catch (Exception e) { return null; }
    }

    // ============ getSeasonAnime (with upcoming fallback) ============
    public String getSeasonAnime(int year, String season) {
        try {
            if (season == null || season.isBlank()) {
                return getCurrentSeasonAnime();
            }
            String result = getSeasonalAnimeInternal(season, year,
                    String.format("=== %s %d Season Anime ===",
                            season.substring(0, 1).toUpperCase() + season.substring(1), year));
            if (result == null || result.contains("No seasonal anime")) {
                // Fallback to upcoming
                log.info("No data for {} {}, falling back to upcoming", season, year);
                return getUpcomingAnime();
            }
            return result;
        } catch (Exception e) { return null; }
    }

    // ============ getUpcomingAnime ============
    public String getUpcomingAnime() {
        try {
            String url = "https://api.jikan.moe/v4/seasons/upcoming?sfw=true";
            String result = httpGet(url);
            JSONArray data = JSON.parseObject(result).getJSONArray("data");
            if (data == null || data.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("=== Upcoming Anime ===\n\n");
            int count = Math.min(data.size(), 10);
            for (int i = 0; i < count; i++)
                sb.append(formatAnimeEntry(data.getJSONObject(i), i + 1));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    // ============ getTopAnime ============
    public String getTopAnime(String filter, int limit) {
        try {
            String url = String.format("https://api.jikan.moe/v4/top/anime?filter=%s&limit=%d&sfw=true",
                    filter != null ? filter : "bypopularity", limit);
            String result = httpGet(url);
            JSONArray data = JSON.parseObject(result).getJSONArray("data");
            if (data == null || data.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("=== Top Anime Rankings ===\n\n");
            int count = Math.min(data.size(), limit);
            for (int i = 0; i < count; i++)
                sb.append(formatAnimeEntry(data.getJSONObject(i), i + 1));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    // ============ searchAnimeCharacter ============
    public String searchCharacter(String query, int page, int limit) {
        try {
            String encoded = java.net.URLEncoder.encode(query, "UTF-8");
            String url = String.format("https://api.jikan.moe/v4/characters?q=%s&page=%d&limit=%d&order_by=favorites&sort=desc",
                    encoded, page, limit);
            String result = httpGet(url);
            JSONArray data = JSON.parseObject(result).getJSONArray("data");
            if (data == null || data.isEmpty())
                return null;
            StringBuilder sb = new StringBuilder();
            sb.append("=== Character Search Results ===\n\n");
            int count = Math.min(data.size(), limit);
            for (int i = 0; i < count; i++) {
                JSONObject ch = data.getJSONObject(i);
                sb.append("[").append(i + 1).append("] ").append(ch.getString("name"));
                String nameJa = ch.getString("name_kanji");
                if (nameJa != null && !nameJa.isBlank()) sb.append(" (").append(nameJa).append(")");
                sb.append("\n");
                Integer favorites = ch.getInteger("favorites");
                if (favorites != null) sb.append("    Favorites: ").append(favorites).append("\n");
                String about = ch.getString("about");
                if (about != null && !about.isBlank()) {
                    sb.append("    About: ").append(about.length() > 200 ? about.substring(0, 200) + "..." : about).append("\n");
                }
                JSONArray animeRoles = ch.getJSONArray("anime");
                if (animeRoles != null && !animeRoles.isEmpty()) {
                    sb.append("    Appears in: ");
                    for (int j = 0; j < Math.min(animeRoles.size(), 5); j++) {
                        JSONObject role = animeRoles.getJSONObject(j);
                        JSONObject animeObj = role.getJSONObject("anime");
                        if (animeObj != null) {
                            if (j > 0) sb.append(", ");
                            sb.append(animeObj.getString("title"));
                        }
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    // ============ getAnimeNews ============
    public String getAnimeNews(int page, int limit) {
        try {
            String url = String.format("https://api.jikan.moe/v4/anime?order_by=start_date&sort=desc&page=%d&limit=%d&sfw=true", page, limit);
            String result = httpGet(url);
            JSONArray data = JSON.parseObject(result).getJSONArray("data");
            if (data == null || data.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("=== Latest Anime Releases ===\n\n");
            int count = Math.min(data.size(), limit);
            for (int i = 0; i < count; i++)
                sb.append(formatAnimeEntry(data.getJSONObject(i), i + 1));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    // ============ Internals ============

    private String getSeasonalAnimeInternal(String season, int year, String header) {
        try {
            String url = String.format("https://api.jikan.moe/v4/seasons/%d/%s?sfw=true", year, season);
            String result = httpGet(url);
            JSONArray data = JSON.parseObject(result).getJSONArray("data");
            if (data == null || data.isEmpty())
                return "No seasonal anime data for " + season + " " + year + ".";
            StringBuilder sb = new StringBuilder();
            sb.append(header).append("\n\n");
            int count = Math.min(data.size(), 10);
            for (int i = 0; i < count; i++)
                sb.append(formatAnimeEntry(data.getJSONObject(i), i + 1));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private String formatAnimeEntry(JSONObject anime, int index) {
        String title = anime.getString("title");
        String titleEn = anime.getString("title_english");
        String type = anime.getString("type");
        String status = anime.getString("status");
        Double score = anime.getDouble("score");
        Integer episodes = anime.getInteger("episodes");
        String synopsis = anime.getString("synopsis");
        Integer malId = anime.getInteger("mal_id");
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(index).append("] ").append(title);
        if (score != null) sb.append("  ").append(String.format("%.1f", score));
        sb.append("\n");
        if (titleEn != null && !titleEn.isBlank() && !titleEn.equals(title))
            sb.append("    EN: ").append(titleEn).append("\n");
        sb.append(String.format("    Type: %s | Status: %s | Ep: %s | ID: %d\n",
                type != null ? type : "?", status != null ? status : "?",
                episodes != null ? String.valueOf(episodes) : "?", malId != null ? malId : 0));
        if (synopsis != null && !synopsis.isBlank())
            sb.append("    Synopsis: ").append(synopsis.length() > 250 ? synopsis.substring(0, 250) + "..." : synopsis).append("\n");
        sb.append("\n");
        return sb.toString();
    }

    private String formatAnimeDetail(JSONObject anime) {
        StringBuilder sb = new StringBuilder();
        String title = anime.getString("title");
        Double score = anime.getDouble("score");
        sb.append("=== ").append(title);
        if (score != null) sb.append("  ").append(String.format("%.1f", score));
        sb.append(" ===\n\n");
        String titleEn = anime.getString("title_english");
        if (titleEn != null && !titleEn.isBlank()) sb.append("English: ").append(titleEn).append("\n");
        sb.append("Type: ").append(anime.getString("type")).append("\n");
        sb.append("Status: ").append(anime.getString("status")).append("\n");
        sb.append("Episodes: ").append(anime.getInteger("episodes")).append("\n");
        sb.append("Score: ").append(score).append("\n");
        sb.append("Rank: #").append(anime.getInteger("rank")).append("\n");
        sb.append("Popularity: #").append(anime.getInteger("popularity")).append("\n");
        JSONObject aired = anime.getJSONObject("aired");
        if (aired != null) sb.append("Aired: ").append(aired.getString("string")).append("\n");
        String synopsis = anime.getString("synopsis");
        if (synopsis != null) sb.append("\nSynopsis:\n").append(synopsis).append("\n");
        JSONArray genres = anime.getJSONArray("genres");
        if (genres != null && !genres.isEmpty()) {
            sb.append("\nGenres: ");
            for (int i = 0; i < genres.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(genres.getJSONObject(i).getString("name"));
            }
            sb.append("\n");
        }
        JSONArray studios = anime.getJSONArray("studios");
        if (studios != null && !studios.isEmpty()) {
            sb.append("Studios: ");
            for (int i = 0; i < studios.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(studios.getJSONObject(i).getString("name"));
            }
            sb.append("\n");
        }
        Integer malId = anime.getInteger("mal_id");
        sb.append("\nMAL: https://myanimelist.net/anime/").append(malId).append("\n");
        return sb.toString();
    }

    private String httpGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url)).timeout(Duration.ofSeconds(3))
                .header("User-Agent", "AnimeAI/1.0").GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) {
            Thread.sleep(1500);
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        return response.body();
    }

    // ============ AniList GraphQL (reliable fallback) ============

    public String searchAnimeAniList(String query, int limit) {
        try {
            String escaped = query.replace("\\", "\\\\").replace("\"", "\\\"");
            String gql = "{\"query\":\"query($q:String!,$l:Int){Page(perPage:$l){media(search:$q,type:ANIME,sort:POPULARITY_DESC){id title{romaji english native}format status episodes averageScore genres description}}}\",\"variables\":{\"q\":\"" + escaped + "\",\"l\":" + limit + "}}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://graphql.anilist.co"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gql))
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JSONObject json = JSON.parseObject(resp.body());
            JSONArray media = json.getJSONObject("data").getJSONObject("Page").getJSONArray("media");
            if (media == null || media.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("=== Anime Search (AniList) ===\n\n");
            for (int i = 0; i < Math.min(media.size(), limit); i++) {
                JSONObject m = media.getJSONObject(i);
                JSONObject title = m.getJSONObject("title");
                sb.append("[").append(i + 1).append("] ").append(title.getString("romaji"));
                String eng = title.getString("english");
                if (eng != null && !eng.isBlank()) sb.append(" / ").append(eng);
                Integer score = m.getInteger("averageScore");
                if (score != null) sb.append("  ").append(score).append("%");
                sb.append("\n");
                sb.append("    Type: ").append(m.getString("format")).append(" | Ep: ").append(m.getInteger("episodes"));
                sb.append(" | Status: ").append(m.getString("status")).append("\n");
                JSONArray genres = m.getJSONArray("genres");
                if (genres != null && !genres.isEmpty()) {
                    sb.append("    Genres: ");
                    for (int j = 0; j < Math.min(genres.size(), 5); j++) {
                        if (j > 0) sb.append(", "); sb.append(genres.getString(j));
                    }
                    sb.append("\n");
                }
                String desc = m.getString("description");
                if (desc != null && !desc.isBlank()) {
                    String clean = desc.replaceAll("<[^>]+>", "");
                    sb.append("    ").append(clean.length() > 200 ? clean.substring(0, 200) + "..." : clean).append("\n");
                }
                sb.append("    AniList: https://anilist.co/anime/").append(m.getInteger("id")).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public String getSeasonalAniList(String season, int year) {
        try {
            String[] seasons = {"WINTER","SPRING","SUMMER","FALL"};
            String[] names = {"winter","spring","summer","fall"};
            String s = "FALL"; int idx = 3;
            for (int i = 0; i < 4; i++) { if (names[i].equalsIgnoreCase(season)) { s = seasons[i]; idx = i; break; } }
            String gql = "{\"query\":\"query($s:MediaSeason,$y:Int){Page(perPage:10){media(season:$s,seasonYear:$y,type:ANIME,sort:POPULARITY_DESC){id title{romaji english}averageScore episodes format status genres description}}}\",\"variables\":{\"s\":\"" + s + "\",\"y\":" + year + "}}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://graphql.anilist.co"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gql)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JSONArray media = JSON.parseObject(resp.body()).getJSONObject("data").getJSONObject("Page").getJSONArray("media");
            if (media == null || media.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("=== ").append(names[idx].substring(0,1).toUpperCase()).append(names[idx].substring(1));
            sb.append(" ").append(year).append(" Anime (AniList) ===\n\n");
            for (int i = 0; i < Math.min(media.size(), 10); i++) {
                JSONObject m = media.getJSONObject(i);
                JSONObject title = m.getJSONObject("title");
                sb.append("[").append(i + 1).append("] ").append(title.getString("romaji"));
                Integer score = m.getInteger("averageScore");
                if (score != null) sb.append("  ").append(score).append("%");
                sb.append("\n    Format: ").append(m.getString("format")).append(" | Ep: ").append(m.getInteger("episodes")).append("\n");
                JSONArray genres = m.getJSONArray("genres");
                if (genres != null && !genres.isEmpty()) {
                    sb.append("    Genres: ");
                    for (int j = 0; j < Math.min(genres.size(), 4); j++) {
                        if (j > 0) sb.append(", "); sb.append(genres.getString(j));
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    // ============ AniList: Top / Upcoming / Movie ============

    public String getTopAnimeAniList(int limit) {
        try {
            String gql = "{\"query\":\"query($l:Int){Page(perPage:$l){media(type:ANIME,sort:SCORE_DESC,status_in:[FINISHED,RELEASING]){id title{romaji english}averageScore format episodes genres status}}}\",\"variables\":{\"l\":" + limit + "}}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://graphql.anilist.co"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gql)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JSONArray media = JSON.parseObject(resp.body()).getJSONObject("data").getJSONObject("Page").getJSONArray("media");
            if (media == null || media.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("=== Top Rated Anime (AniList) ===\n\n");
            for (int i = 0; i < Math.min(media.size(), limit); i++) {
                JSONObject m = media.getJSONObject(i);
                JSONObject title = m.getJSONObject("title");
                sb.append("[").append(i + 1).append("] ").append(title.getString("romaji"));
                Integer score = m.getInteger("averageScore");
                if (score != null) sb.append("  ").append(score).append("%");
                sb.append("\n    Format: ").append(m.getString("format")).append(" | Ep: ").append(m.getInteger("episodes"));
                sb.append(" | Status: ").append(m.getString("status")).append("\n");
                JSONArray genres = m.getJSONArray("genres");
                if (genres != null && !genres.isEmpty()) {
                    sb.append("    Genres: ");
                    for (int j = 0; j < Math.min(genres.size(), 4); j++) {
                        if (j > 0) sb.append(", "); sb.append(genres.getString(j));
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public String getPopularAnimeAniList(int limit) {
        try {
            String gql = "{\"query\":\"query($l:Int){Page(perPage:$l){media(type:ANIME,sort:POPULARITY_DESC){id title{romaji english}averageScore format episodes genres status}}}\",\"variables\":{\"l\":" + limit + "}}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://graphql.anilist.co"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gql)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JSONArray media = JSON.parseObject(resp.body()).getJSONObject("data").getJSONObject("Page").getJSONArray("media");
            if (media == null || media.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("=== Most Popular Anime (AniList) ===\n\n");
            for (int i = 0; i < Math.min(media.size(), limit); i++) {
                JSONObject m = media.getJSONObject(i);
                JSONObject title = m.getJSONObject("title");
                sb.append("[").append(i + 1).append("] ").append(title.getString("romaji"));
                Integer score = m.getInteger("averageScore");
                if (score != null) sb.append("  ").append(score).append("%");
                sb.append("\n    Format: ").append(m.getString("format")).append(" | Ep: ").append(m.getInteger("episodes")).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public String getUpcomingAnimeAniList(int limit) {
        try {
            // AniList: season later than current
            LocalDate now2 = LocalDate.now(); int y2 = now2.getYear(); int m2 = now2.getMonthValue();
            String s = m2 <= 3 ? "WINTER" : m2 <= 6 ? "SPRING" : m2 <= 9 ? "SUMMER" : "FALL";
            // Get current season + next season
            String nextS = s.equals("WINTER") ? "SPRING" : s.equals("SPRING") ? "SUMMER" : s.equals("SUMMER") ? "FALL" : "WINTER";
            int nextY = s.equals("FALL") ? y2 + 1 : y2;
            String gql = "{\"query\":\"query($s1:MediaSeason,$y1:Int,$s2:MediaSeason,$y2:Int,$l:Int){Page(perPage:$l){media(season_in:[$s1,$s2],seasonYear_in:[$y1,$y2],type:ANIME,sort:POPULARITY_DESC){id title{romaji english}averageScore format episodes genres status}}}\",\"variables\":{\"s1\":\"" + s + "\",\"y1\":" + y2 + ",\"s2\":\"" + nextS + "\",\"y2\":" + nextY + ",\"l\":" + limit + "}}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://graphql.anilist.co"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gql)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JSONArray media = JSON.parseObject(resp.body()).getJSONObject("data").getJSONObject("Page").getJSONArray("media");
            if (media == null || media.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("=== Upcoming Anime (AniList) ===\n\n");
            for (int i = 0; i < Math.min(media.size(), limit); i++) {
                JSONObject m = media.getJSONObject(i);
                JSONObject title = m.getJSONObject("title");
                sb.append("[").append(i + 1).append("] ").append(title.getString("romaji"));
                Integer score = m.getInteger("averageScore");
                if (score != null) sb.append("  ").append(score).append("%");
                sb.append("\n    Format: ").append(m.getString("format")).append(" | Ep: ").append(m.getInteger("episodes"));
                sb.append(" | Status: ").append(m.getString("status")).append("\n");
                JSONArray genres = m.getJSONArray("genres");
                if (genres != null && !genres.isEmpty()) {
                    sb.append("    Genres: ");
                    for (int j = 0; j < Math.min(genres.size(), 4); j++) {
                        if (j > 0) sb.append(", "); sb.append(genres.getString(j));
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public String searchAnimeMovie(String query, int limit) {
        try {
            String escaped = query.replace("\\", "\\\\").replace("\"", "\\\"");
            String gql = "{\"query\":\"query($q:String!,$l:Int){Page(perPage:$l){media(search:$q,format:MOVIE,type:ANIME,sort:POPULARITY_DESC){id title{romaji english native}averageScore status duration genres description startDate{year month day}}}}\",\"variables\":{\"q\":\"" + escaped + "\",\"l\":" + limit + "}}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://graphql.anilist.co"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gql)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JSONArray media = JSON.parseObject(resp.body()).getJSONObject("data").getJSONObject("Page").getJSONArray("media");
            if (media == null || media.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("=== Anime Movie Search ===\n\n");
            for (int i = 0; i < Math.min(media.size(), limit); i++) {
                JSONObject m = media.getJSONObject(i);
                JSONObject title = m.getJSONObject("title");
                sb.append("[").append(i + 1).append("] ").append(title.getString("romaji"));
                String nat = title.getString("native");
                if (nat != null && !nat.isBlank()) sb.append(" / ").append(nat);
                Integer score = m.getInteger("averageScore");
                if (score != null) sb.append("  ").append(score).append("%");
                sb.append("\n    Duration: ").append(m.getInteger("duration")).append(" min");
                sb.append(" | Status: ").append(m.getString("status")).append("\n");
                JSONObject sd = m.getJSONObject("startDate");
                if (sd != null) sb.append("    Release: ").append(sd.getInteger("year")).append("-").append(sd.getInteger("month")).append("-").append(sd.getInteger("day")).append("\n");
                String desc = m.getString("description");
                if (desc != null && !desc.isBlank()) {
                    String clean = desc.replaceAll("<[^>]+>", "");
                    sb.append("    ").append(clean.length() > 200 ? clean.substring(0, 200) + "..." : clean).append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public String getUpcomingMoviesAniList(int limit) {
        try {
            LocalDate now = LocalDate.now();
            int y = now.getYear();
            String gql = "{\"query\":\"query($y:Int,$l:Int){Page(perPage:$l){media(format:MOVIE,type:ANIME,status:NOT_YET_RELEASED,seasonYear_greater:" + (y - 1) + ",sort:POPULARITY_DESC){id title{romaji english native}averageScore duration genres startDate{year month day}}}}\",\"variables\":{\"y\":" + y + ",\"l\":" + limit + "}}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://graphql.anilist.co"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gql)).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JSONArray media = JSON.parseObject(resp.body()).getJSONObject("data").getJSONObject("Page").getJSONArray("media");
            if (media == null || media.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            sb.append("=== Upcoming Anime Movies (AniList) ===\n\n");
            for (int i = 0; i < Math.min(media.size(), limit); i++) {
                JSONObject m = media.getJSONObject(i);
                JSONObject title = m.getJSONObject("title");
                sb.append("[").append(i + 1).append("] ").append(title.getString("romaji"));
                String nat = title.getString("native");
                if (nat != null && !nat.isBlank()) sb.append(" / ").append(nat);
                Integer score = m.getInteger("averageScore");
                if (score != null) sb.append("  ").append(score).append("%");
                sb.append("\n    Duration: ").append(m.getInteger("duration")).append(" min");
                sb.append(" | Status: ").append(m.getString("status")).append("\n");
                JSONObject sd = m.getJSONObject("startDate");
                if (sd != null) sb.append("    Release: ").append(sd.getInteger("year")).append("-").append(sd.getInteger("month")).append("-").append(sd.getInteger("day")).append("\n");
                JSONArray genres = m.getJSONArray("genres");
                if (genres != null && !genres.isEmpty()) {
                    sb.append("    Genres: ");
                    for (int j = 0; j < Math.min(genres.size(), 4); j++) {
                        if (j > 0) sb.append(", "); sb.append(genres.getString(j));
                    }
                    sb.append("\n");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    public String getRecentAnimeMoviesAniList(int limit) {
        try {
            LocalDate now = LocalDate.now();
            int from = now.minusYears(1).getYear() * 10000 + now.minusYears(1).getMonthValue() * 100 + now.minusYears(1).getDayOfMonth();
            int today = now.getYear() * 10000 + now.getMonthValue() * 100 + now.getDayOfMonth();
            int until = now.plusYears(1).getYear() * 10000 + now.plusYears(1).getMonthValue() * 100 + now.plusYears(1).getDayOfMonth();
            String fields = "id siteUrl title{romaji english native} status averageScore duration genres startDate{year month day} studios(isMain:true){nodes{name}}";
            String query = "query($from:FuzzyDateInt,$today:FuzzyDateInt,$until:FuzzyDateInt,$limit:Int){recent:Page(perPage:$limit){media(type:ANIME,format:MOVIE,startDate_greater:$from,startDate_lesser:$today,sort:POPULARITY_DESC){" + fields + "}} upcoming:Page(perPage:$limit){media(type:ANIME,format:MOVIE,status:NOT_YET_RELEASED,startDate_greater:$today,startDate_lesser:$until,sort:POPULARITY_DESC){" + fields + "}}}";
            JSONObject payload = new JSONObject();
            payload.put("query", query);
            payload.put("variables", JSONObject.of("from", from, "today", today, "until", until, "limit", Math.max(5, limit / 2)));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://graphql.anilist.co"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("User-Agent", "AnimeAI/1.0")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            JSONObject data = JSON.parseObject(response.body()).getJSONObject("data");
            JSONArray media = data.getJSONObject("recent").getJSONArray("media");
            JSONArray upcoming = data.getJSONObject("upcoming").getJSONArray("media");
            if (upcoming != null) media.addAll(upcoming);
            if (media == null || media.isEmpty()) return null;
            StringBuilder result = new StringBuilder("近期动漫电影（过去一年已上映＋未来一年即将上映；AniList 实时数据，查询日期 ").append(now).append("）\n\n");
            for (int index = 0; index < Math.min(media.size(), limit); index++) {
                JSONObject item = media.getJSONObject(index);
                JSONObject titles = item.getJSONObject("title");
                String title = titles.getString("native");
                if (title == null || title.isBlank()) title = titles.getString("romaji");
                JSONObject date = item.getJSONObject("startDate");
                if (index == 0) result.append("【近期已上映】\n");
                if (index == media.size() - (upcoming == null ? 0 : upcoming.size()) && upcoming != null && !upcoming.isEmpty()) result.append("\n【即将上映】\n");
                result.append(index + 1).append(". ").append(title);
                String english = titles.getString("english");
                if (english != null && !english.isBlank()) result.append(" / ").append(english);
                result.append("\n   日期：").append(date.getInteger("year"));
                if (date.getInteger("month") != null) result.append("-").append(date.getInteger("month"));
                if (date.getInteger("day") != null) result.append("-").append(date.getInteger("day"));
                result.append("｜状态：").append(item.getString("status"));
                Integer score = item.getInteger("averageScore");
                if (score != null) result.append("｜评分：").append(score).append("%");
                Integer duration = item.getInteger("duration");
                if (duration != null) result.append("｜时长：").append(duration).append("分钟");
                JSONArray studios = item.getJSONObject("studios").getJSONArray("nodes");
                if (studios != null && !studios.isEmpty()) result.append("｜制作：").append(studios.getJSONObject(0).getString("name"));
                result.append("\n   来源：").append(item.getString("siteUrl")).append("\n\n");
                MovieTicketService.TicketLink ticket = movieTicketService.findMaoyanMovie(title);
                if (ticket != null) {
                    result.append("   详情：猫眼《").append(ticket.title()).append("》 ").append(ticket.url()).append("\n")
                            .append("   上映信息：").append(ticket.releaseInfo()).append("\n")
                            .append("   购票状态：").append(ticket.purchaseStatus()).append("\n");
                    if (ticket.purchasable()) result.append("   购票：打开上方猫眼详情页选择影院和场次\n");
                    result.append("\n");
                } else {
                    result.append("   国内购票：猫眼/淘票票暂未收录该片，因此没有可用的电影详情购票链接。\n\n");
                }
            }
            result.append("想继续查购票和路线，请告诉我你所在的城市/区县或商圈，例如：‘我在杭州滨江，帮我找附近能看的影院’。\n")
                    .append("选定电影后还可以说：‘提醒我上映当天去看’，我会生成日历提醒。\n")
                    .append("说明：具体排片与中国大陆上映状态以猫眼/淘票票页面和影院为准。");
            return result.toString();
        } catch (Exception error) {
            return null;
        }
    }

    public String getAnimeAiringSchedule(LocalDate date, int limit) {
        try {
            long start = date.atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond();
            long end = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toEpochSecond() - 1;
            String query = "query($start:Int,$end:Int,$limit:Int){Page(perPage:$limit){airingSchedules(airingAt_greater:$start,airingAt_lesser:$end,sort:TIME){episode airingAt media{id siteUrl title{native romaji english}coverImage{large}}}}}";
            JSONObject variables = JSONObject.of("start", start, "end", end, "limit", limit);
            JSONObject data = aniList(query, variables);
            JSONArray schedules = data.getJSONObject("Page").getJSONArray("airingSchedules");
            if (schedules == null || schedules.isEmpty()) return "当天没有查到播出记录。";
            StringBuilder result = new StringBuilder("Anime airing schedule for ").append(date).append("\n\n");
            for (int index = 0; index < schedules.size(); index++) {
                JSONObject schedule = schedules.getJSONObject(index);
                JSONObject media = schedule.getJSONObject("media");
                result.append(index + 1).append(". ").append(animeTitle(media.getJSONObject("title")))
                        .append("｜第 ").append(schedule.getIntValue("episode")).append(" 集")
                        .append("｜").append(java.time.Instant.ofEpochSecond(schedule.getLongValue("airingAt"))
                                .atZone(java.time.ZoneId.systemDefault()).toLocalTime())
                        .append("\n   ").append(media.getString("siteUrl")).append("\n");
            }
            return result.toString();
        } catch (Exception error) {
            return null;
        }
    }

    public String getAnimeWatchLinks(String title) {
        try {
            String query = "query($title:String){Media(search:$title,type:ANIME){id siteUrl title{native romaji english}externalLinks{site url type language color icon}streamingEpisodes{title url site thumbnail}}}";
            JSONObject media = aniList(query, JSONObject.of("title", title)).getJSONObject("Media");
            if (media == null) return null;
            StringBuilder result = new StringBuilder("《").append(animeTitle(media.getJSONObject("title"))).append("》观看与资料入口\n\n");
            JSONArray links = media.getJSONArray("externalLinks");
            int count = 0;
            for (int index = 0; links != null && index < links.size(); index++) {
                JSONObject link = links.getJSONObject(index);
                if (!"STREAMING".equals(link.getString("type"))) continue;
                result.append("- ").append(link.getString("site")).append("：").append(link.getString("url")).append("\n");
                count++;
            }
            result.append("- AniList：").append(media.getString("siteUrl")).append("\n");
            if (count == 0) result.append("\n未收录官方播放平台。请告诉我你所在国家/地区，我可以继续联网搜索当地正版平台。\n");
            result.append("\n不同地区版权不同，请以链接打开后的地区可用性为准。");
            return result.toString();
        } catch (Exception error) {
            return null;
        }
    }

    public String getAnimeRelations(String title) {
        try {
            String query = "query($title:String){Media(search:$title,type:ANIME){siteUrl title{native romaji english}relations{edges{relationType(version:2) node{siteUrl format status title{native romaji english}}}}}}";
            JSONObject media = aniList(query, JSONObject.of("title", title)).getJSONObject("Media");
            if (media == null) return null;
            JSONArray edges = media.getJSONObject("relations").getJSONArray("edges");
            StringBuilder result = new StringBuilder("《").append(animeTitle(media.getJSONObject("title"))).append("》系列观看关系\n\n");
            for (int index = 0; edges != null && index < edges.size(); index++) {
                JSONObject edge = edges.getJSONObject(index);
                JSONObject node = edge.getJSONObject("node");
                result.append("- ").append(edge.getString("relationType")).append("：")
                        .append(animeTitle(node.getJSONObject("title"))).append("（")
                        .append(node.getString("format")).append(" / ").append(node.getString("status")).append("）\n  ")
                        .append(node.getString("siteUrl")).append("\n");
            }
            return result.toString();
        } catch (Exception error) {
            return null;
        }
    }

    public String getAnimeRecommendations(String title, int limit) {
        try {
            String query = "query($title:String,$limit:Int){Media(search:$title,type:ANIME){title{native romaji english}recommendations(page:1,perPage:$limit,sort:RATING_DESC){nodes{rating mediaRecommendation{id siteUrl format averageScore title{native romaji english}genres}}}}}";
            JSONObject media = aniList(query, JSONObject.of("title", title, "limit", limit)).getJSONObject("Media");
            if (media == null) return null;
            JSONArray nodes = media.getJSONObject("recommendations").getJSONArray("nodes");
            StringBuilder result = new StringBuilder("看完《").append(animeTitle(media.getJSONObject("title"))).append("》可以接着看\n\n");
            for (int index = 0; nodes != null && index < nodes.size(); index++) {
                JSONObject node = nodes.getJSONObject(index);
                JSONObject recommendation = node.getJSONObject("mediaRecommendation");
                if (recommendation == null) continue;
                result.append(index + 1).append(". ").append(animeTitle(recommendation.getJSONObject("title")))
                        .append("｜").append(recommendation.getString("format"));
                Integer score = recommendation.getInteger("averageScore");
                if (score != null) result.append("｜评分 ").append(score).append("%");
                result.append("\n   ").append(recommendation.getString("siteUrl")).append("\n");
            }
            result.append("\n告诉我你更看重剧情、画风、恋爱、战斗还是治愈，我可以继续缩小推荐范围。");
            return result.toString();
        } catch (Exception error) {
            return null;
        }
    }

    private JSONObject aniList(String query, JSONObject variables) throws Exception {
        JSONObject payload = JSONObject.of("query", query, "variables", variables);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("https://graphql.anilist.co"))
                .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/json")
                .header("Accept", "application/json").header("User-Agent", "AnimeAI/1.0")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString())).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("AniList HTTP " + response.statusCode());
        JSONObject body = JSON.parseObject(response.body());
        if (body.getJSONArray("errors") != null) throw new IllegalStateException(body.getJSONArray("errors").toJSONString());
        return body.getJSONObject("data");
    }

    private String animeTitle(JSONObject title) {
        String value = title.getString("native");
        if (value == null || value.isBlank()) value = title.getString("english");
        if (value == null || value.isBlank()) value = title.getString("romaji");
        return value;
    }

    public String getDomesticMovieTicketLink(String title) {
        MovieTicketService.TicketLink ticket = movieTicketService.findMaoyanMovie(title);
        if (ticket == null) return "猫眼暂未收录《" + title + "》，当前没有可直接打开的国内电影详情/购票页。";
        return "猫眼匹配：《" + ticket.title() + "》\n"
                + "上映信息：" + ticket.releaseInfo() + "\n"
                + "购票状态：" + ticket.purchaseStatus() + "\n"
                + "详情链接：" + ticket.url()
                + (ticket.purchasable() ? "\n购票方式：打开详情链接后选择影院和场次。" : "");
    }
}
