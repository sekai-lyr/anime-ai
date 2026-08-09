package com.example.demo.chat.config;

import com.example.demo.chat.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
/**
对话记忆存储配置类。
 * 根据配置选择对话记忆的存储方式（内存/数据库）。
 */
public class ChatMemoryConfig {

    private static final Logger logger = LoggerFactory.getLogger(ChatMemoryConfig.class);

    @Bean
    public RedisChatMemoryRepository redisChatMemoryRepository(StringRedisTemplate redisTemplate) {
        logger.info("========== Initializing RedisChatMemoryRepository ==========");
        return new RedisChatMemoryRepository(redisTemplate);
    }

    @Bean
    public DatabaseChatMemoryRepository databaseChatMemoryRepository(ConversationRepository conversationRepository,
                                                                      MessageRepository messageRepository) {
        logger.info("========== Initializing DatabaseChatMemoryRepository ==========");
        return new DatabaseChatMemoryRepository(conversationRepository, messageRepository);
    }

    @Bean
    @Primary
    public ChatMemoryRepository cachedChatMemoryRepository(RedisChatMemoryRepository redisChatMemoryRepository,
                                                           DatabaseChatMemoryRepository databaseChatMemoryRepository) {
        logger.info("========== Using CachedChatMemoryRepository (Redis + Database) ==========");
        return new CachedChatMemoryRepository(redisChatMemoryRepository, databaseChatMemoryRepository);
    }
}