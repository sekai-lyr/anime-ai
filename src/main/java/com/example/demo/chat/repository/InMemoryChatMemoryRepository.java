package com.example.demo.chat.repository;

import com.example.demo.chat.ChatMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
基于内存的对话记忆仓库实现。
 * 使用ConcurrentHashMap在内存中存储对话记忆，适用于开发测试。
 */
public class InMemoryChatMemoryRepository implements ChatMemoryRepository {

    private final ConcurrentHashMap<String, List<ChatMessage>> memory = new ConcurrentHashMap<>();

    @Override
    public List<ChatMessage> getMessages(String conversationId) {
        return memory.getOrDefault(conversationId, new ArrayList<>());
    }

    @Override
    public void saveMessages(String conversationId, List<ChatMessage> messages) {
        memory.put(conversationId, new ArrayList<>(messages));
    }

    @Override
    public void addMessage(String conversationId, ChatMessage message) {
        memory.compute(conversationId, (key, existing) -> {
            List<ChatMessage> list = existing != null ? existing : new ArrayList<>();
            list.add(message);
            return list;
        });
    }

    @Override
    public void clear(String conversationId) {
        memory.remove(conversationId);
    }

    @Override
    public boolean exists(String conversationId) {
        return memory.containsKey(conversationId);
    }
}