package com.example.demo.movie;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MovieTicketService {

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(2))
            .readTimeout(Duration.ofSeconds(3))
            .build();
    private final Map<String, TicketLink> cache = new ConcurrentHashMap<>();

    public TicketLink findMaoyanMovie(String title) {
        if (title == null || title.isBlank()) return null;
        TicketLink cached = cache.get(title);
        if (cached != null) return cached;
        try {
            String url = "https://m.maoyan.com/ajax/search?kw="
                    + URLEncoder.encode(title, StandardCharsets.UTF_8) + "&cityId=1&stype=-1";
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Mobile Safari/537.36")
                    .header("Referer", "https://m.maoyan.com/").build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                JSONObject movies = JSON.parseObject(response.body().string()).getJSONObject("movies");
                JSONArray list = movies == null ? null : movies.getJSONArray("list");
                if (list == null || list.isEmpty()) return null;
                JSONObject movie = list.getJSONObject(0);
                TicketLink result = new TicketLink(movie.getLongValue("id"), movie.getString("nm"),
                        movie.getString("pubDesc"), movie.getIntValue("showst"),
                        "https://m.maoyan.com/asgard/movie/" + movie.getLongValue("id"));
                cache.put(title, result);
                return result;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    public record TicketLink(long movieId, String title, String releaseInfo, int showStatus, String url) {
        public boolean hasMainlandRelease() {
            return releaseInfo != null && releaseInfo.contains("中国大陆");
        }

        public boolean purchasable() {
            return hasMainlandRelease() && (showStatus == 3 || showStatus == 4);
        }

        public String purchaseStatus() {
            if (!hasMainlandRelease()) return "中国大陆未定档，当前不可购票";
            if (showStatus == 4) return "中国大陆已定档并开启预售，可购票";
            if (showStatus == 3) return "中国大陆正在上映，可购票";
            return "中国大陆曾定档，但当前无可购场次（可能未开售或已下映）";
        }
    }
}
