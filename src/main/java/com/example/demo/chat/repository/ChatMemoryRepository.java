package com.example.demo.chat.repository;

import com.example.demo.chat.ChatMessage;

import java.util.List;

/**
对话记忆仓库接口。
 * 定义对话记忆存储的标准操作接口，支持多种实现（内存、数据库等）。
 */
public interface ChatMemoryRepository {

    List<ChatMessage> getMessages(String conversationId);

    void saveMessages(String conversationId, List<ChatMessage> messages);

    void addMessage(String conversationId, ChatMessage message);

    void clear(String conversationId);

    boolean exists(String conversationId);
}