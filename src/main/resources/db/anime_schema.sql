-- Anime AI Assistant 数据库初始化脚本
-- 动漫角色档案表
CREATE TABLE IF NOT EXISTS anime_character_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    series_name VARCHAR(200),
    role VARCHAR(100),
    ai_image_url VARCHAR(500),
    traits TEXT,
    voice_actor VARCHAR(100),
    create_time DATETIME
);

-- 动漫番剧档案表
CREATE TABLE IF NOT EXISTS anime_series_profile (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    genre VARCHAR(200),
    episodes INT,
    rating VARCHAR(10),
    studio VARCHAR(100),
    ai_image_url VARCHAR(500),
    synopsis TEXT,
    recommend_reason TEXT,
    create_time DATETIME
);

-- 动漫目标表（用户关注的动漫角色或番剧）
CREATE TABLE IF NOT EXISTS anime_targets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    type ENUM('CHARACTER','SERIES') NOT NULL,
    series_name VARCHAR(200),
    genre VARCHAR(100),
    avatar_url VARCHAR(500),
    status VARCHAR(50),
    description TEXT,
    metadata JSON,
    created_at DATETIME,
    updated_at DATETIME
);

-- 追番记录表
CREATE TABLE IF NOT EXISTS anime_watch_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    target_type ENUM('CHARACTER','SERIES') NOT NULL,
    target_id BIGINT NOT NULL,
    record_type ENUM('WATCH','REVIEW','RECOMMEND','REMINDER','NOTE','RATING','FAVORITE') NOT NULL,
    title VARCHAR(200),
    content TEXT,
    metadata JSON,
    is_completed BOOLEAN DEFAULT FALSE,
    reminder_time DATETIME,
    created_at DATETIME
);
