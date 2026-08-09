package com.example.demo.chat.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "anime_series_profile")
public class AnimeSeriesProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "genre", length = 200)
    private String genre;

    @Column(name = "episodes")
    private Integer episodes;

    @Column(name = "rating", length = 10)
    private String rating;

    @Column(name = "studio", length = 100)
    private String studio;

    @Column(name = "ai_image_url", length = 500)
    private String aiImageUrl;

    @Column(name = "synopsis", columnDefinition = "TEXT")
    private String synopsis;

    @Column(name = "recommend_reason", columnDefinition = "TEXT")
    private String recommendReason;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    public AnimeSeriesProfile() {
    }

    public AnimeSeriesProfile(String title, String genre, String aiImageUrl, String synopsis, String recommendReason) {
        this.title = title;
        this.genre = genre;
        this.aiImageUrl = aiImageUrl;
        this.synopsis = synopsis;
        this.recommendReason = recommendReason;
        this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public Integer getEpisodes() { return episodes; }
    public void setEpisodes(Integer episodes) { this.episodes = episodes; }
    public String getRating() { return rating; }
    public void setRating(String rating) { this.rating = rating; }
    public String getStudio() { return studio; }
    public void setStudio(String studio) { this.studio = studio; }
    public String getAiImageUrl() { return aiImageUrl; }
    public void setAiImageUrl(String aiImageUrl) { this.aiImageUrl = aiImageUrl; }
    public String getSynopsis() { return synopsis; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }
    public String getRecommendReason() { return recommendReason; }
    public void setRecommendReason(String recommendReason) { this.recommendReason = recommendReason; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
