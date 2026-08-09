package com.example.demo.chat.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "anime_character_profile")
public class AnimeCharacterProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "series_name", length = 200)
    private String seriesName;

    @Column(name = "role", length = 100)
    private String role;

    @Column(name = "ai_image_url", length = 500)
    private String aiImageUrl;

    @Column(name = "traits", columnDefinition = "TEXT")
    private String traits;

    @Column(name = "voice_actor", length = 100)
    private String voiceActor;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    public AnimeCharacterProfile() {
    }

    public AnimeCharacterProfile(String name, String seriesName, String aiImageUrl, String traits) {
        this.name = name;
        this.seriesName = seriesName;
        this.aiImageUrl = aiImageUrl;
        this.traits = traits;
        this.createTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSeriesName() { return seriesName; }
    public void setSeriesName(String seriesName) { this.seriesName = seriesName; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getAiImageUrl() { return aiImageUrl; }
    public void setAiImageUrl(String aiImageUrl) { this.aiImageUrl = aiImageUrl; }
    public String getTraits() { return traits; }
    public void setTraits(String traits) { this.traits = traits; }
    public String getVoiceActor() { return voiceActor; }
    public void setVoiceActor(String voiceActor) { this.voiceActor = voiceActor; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
