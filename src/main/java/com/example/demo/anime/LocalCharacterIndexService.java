package com.example.demo.anime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class LocalCharacterIndexService {

    private static final URI SEARCH_URI = URI.create("http://127.0.0.1:8095/search");
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private Process process;

    @PostConstruct
    void start() {
        Path root = Path.of("").toAbsolutePath();
        Path python = root.resolve(".venv-clip/Scripts/python.exe");
        Path script = root.resolve("tools/anime_index.py");
        Path vectors = root.resolve("data/anime-index/vectors.npy");
        if (!Files.isRegularFile(python) || !Files.isRegularFile(script) || !Files.isRegularFile(vectors)) {
            log.info("Local anime character index is not built; run tools/setup_anime_index.ps1 to enable it");
            return;
        }
        try {
            process = new ProcessBuilder(python.toString(), script.toString(), "serve", "--port", "8095")
                    .directory(root.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("data/anime-index/service.log").toFile()))
                    .start();
            log.info("Local anime character index started on 127.0.0.1:8095");
        } catch (Exception error) {
            log.warn("Unable to start local anime character index", error);
        }
    }

    public Integer identifyAnime(String imageDataUrl) {
        try {
            JSONObject payload = JSONObject.of("image", imageDataUrl, "limit", 10);
            HttpRequest request = HttpRequest.newBuilder(SEARCH_URI)
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toJSONString()))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return null;
            JSONArray matches = JSON.parseObject(response.body()).getJSONArray("matches");
            if (matches == null || matches.isEmpty()) return null;

            Map<Integer, Double> animeScores = new HashMap<>();
            for (int index = 0; index < matches.size(); index++) {
                JSONObject match = matches.getJSONObject(index);
                animeScores.merge(match.getIntValue("animeId"), match.getDoubleValue("score"), Math::max);
            }
            var ranked = animeScores.entrySet().stream().sorted(Map.Entry.<Integer, Double>comparingByValue().reversed()).toList();
            double best = ranked.getFirst().getValue();
            double second = ranked.size() > 1 ? ranked.get(1).getValue() : 0;
            log.info("Local character index result: animeId={}, score={}, margin={}", ranked.getFirst().getKey(), best, best - second);
            return best >= 0.73 && best - second >= 0.015 ? ranked.getFirst().getKey() : null;
        } catch (Exception error) {
            log.debug("Local anime character index unavailable: {}", error.getMessage());
            return null;
        }
    }

    @PreDestroy
    void stop() {
        if (process != null) process.destroy();
    }
}
