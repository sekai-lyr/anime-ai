package com.example.demo.movie;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reminder")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", length = 64, nullable = false)
    private String conversationId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "remind_time", nullable = false)
    private LocalDateTime remindTime;

    @Column(name = "sent", nullable = false)
    private boolean sent = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public Reminder() {}

    public Reminder(String conversationId, String title, LocalDateTime remindTime) {
        this.conversationId = conversationId;
        this.title = title;
        this.remindTime = remindTime;
        this.sent = false;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getRemindTime() { return remindTime; }
    public void setRemindTime(LocalDateTime remindTime) { this.remindTime = remindTime; }
    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
