package com.example.demo.ai;

/**
 * 用户上下文持有者。
 * 基于ThreadLocal存储当前请求的用户ID和会话ID，使工具方法无需传参即可获取当前用户信息。
 */
public class UserContextHolder {

    private static final ThreadLocal<String> USER_HOLDER = new ThreadLocal<>();
    private static final ThreadLocal<String> CONVERSATION_HOLDER = new ThreadLocal<>();

    public static void setUserId(String userId) {
        USER_HOLDER.set(userId);
    }

    public static String getUserId() {
        return USER_HOLDER.get();
    }

    public static void setConversationId(String conversationId) {
        CONVERSATION_HOLDER.set(conversationId);
    }

    public static String getConversationId() {
        return CONVERSATION_HOLDER.get();
    }

    public static void clear() {
        USER_HOLDER.remove();
        CONVERSATION_HOLDER.remove();
    }
}
