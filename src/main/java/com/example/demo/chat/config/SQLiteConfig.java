package com.example.demo.chat.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * SQLite configuration - only active when "sqlite" profile is enabled.
 * By default, MySQL is used via Spring Boot auto-configuration.
 */
@Configuration
@Profile("sqlite")
public class SQLiteConfig {

    // When sqlite profile is NOT active (default), Spring Boot auto-configures MySQL.
    // When sqlite profile IS active, uncomment the beans below to use SQLite instead.
    // For now, all configuration is handled by application.properties and Spring Boot auto-config.
}
