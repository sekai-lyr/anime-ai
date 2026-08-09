package com.example.demo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "amap")
/**
高德地图API配置类。
 * 加载高德地图API的Key和相关配置参数。
 */
public class AmapConfig {

    private String apiKey;
    private String baseUrl = "https://restapi.amap.com/v3";
    private String webNavBaseUrl = "https://uri.amap.com";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getWebNavBaseUrl() {
        return webNavBaseUrl;
    }

    public void setWebNavBaseUrl(String webNavBaseUrl) {
        this.webNavBaseUrl = webNavBaseUrl;
    }
}