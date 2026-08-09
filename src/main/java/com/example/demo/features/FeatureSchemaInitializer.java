package com.example.demo.features;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
/**
功能特性初始化器。
 * 在应用启动时初始化功能特性表结构和默认数据。
 */
public class FeatureSchemaInitializer {

    private final JdbcTemplate jdbc;

    public FeatureSchemaInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void initialize() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS growth_timeline (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              user_id VARCHAR(64) NOT NULL,
              target_type VARCHAR(16) NOT NULL,
              target_id BIGINT NULL,
              image_url VARCHAR(500) NOT NULL,
              note TEXT NULL,
              ai_annotation TEXT NULL,
              captured_at DATETIME NOT NULL,
              INDEX idx_timeline_user_target(user_id, target_type, target_id, captured_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS disease_diagnosis (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              user_id VARCHAR(64) NOT NULL,
              target_type VARCHAR(16) NOT NULL,
              target_id BIGINT NULL,
              image_url VARCHAR(500) NOT NULL,
              question TEXT NULL,
              diagnosis TEXT NOT NULL,
              created_at DATETIME NOT NULL,
              INDEX idx_diagnosis_user(user_id, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS community_post (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              user_id VARCHAR(64) NOT NULL,
              title VARCHAR(200) NOT NULL,
              content TEXT NOT NULL,
              tags VARCHAR(500) NULL,
              image_url VARCHAR(500) NULL,
              like_count INT NOT NULL DEFAULT 0,
              created_at DATETIME NOT NULL,
              INDEX idx_community_created(created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS community_comment (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              post_id BIGINT NOT NULL,
              user_id VARCHAR(64) NOT NULL,
              content TEXT NOT NULL,
              created_at DATETIME NOT NULL,
              INDEX idx_comment_post(post_id, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS supply_inventory (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              user_id VARCHAR(64) NOT NULL,
              name VARCHAR(120) NOT NULL,
              category VARCHAR(50) NULL,
              quantity DECIMAL(12,2) NOT NULL,
              unit VARCHAR(30) NULL,
              daily_usage DECIMAL(12,2) NOT NULL DEFAULT 0,
              opened_at DATE NULL,
              reorder_threshold DECIMAL(12,2) NOT NULL DEFAULT 0,
              product_id BIGINT NULL,
              updated_at DATETIME NOT NULL,
              INDEX idx_inventory_user(user_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS user_notification (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              user_id VARCHAR(64) NOT NULL,
              title VARCHAR(200) NOT NULL,
              content TEXT NOT NULL,
              notification_type VARCHAR(30) NOT NULL,
              link_url VARCHAR(500) NULL,
              is_read BOOLEAN NOT NULL DEFAULT FALSE,
              created_at DATETIME NOT NULL,
              INDEX idx_notification_user(user_id, is_read, created_at)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS push_subscription (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              user_id VARCHAR(64) NOT NULL,
              device_name VARCHAR(120) NULL,
              endpoint TEXT NOT NULL,
              p256dh VARCHAR(255) NULL,
              auth_secret VARCHAR(255) NULL,
              created_at DATETIME NOT NULL,
              UNIQUE KEY uk_push_endpoint(endpoint(255))
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS daily_brief (
              id BIGINT PRIMARY KEY AUTO_INCREMENT,
              user_id VARCHAR(64) NOT NULL,
              brief_date DATE NOT NULL,
              content TEXT NOT NULL,
              created_at DATETIME NOT NULL,
              UNIQUE KEY uk_brief_user_date(user_id, brief_date)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);
    }
}
