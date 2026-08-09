package com.example.demo.chat.event;

/**
摘要更新事件。
 * Spring事件，在对话摘要更新时发布，供监听器异步处理。
 */
public class SummaryUpdateEvent {
    
    private final String conversationId;
    
    public SummaryUpdateEvent(String conversationId) {
        this.conversationId = conversationId;
    }
    
    public String getConversationId() {
        return conversationId;
    }
}