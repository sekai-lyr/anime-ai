package com.example.demo.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DashScopeConfig {

    private static final Logger log = LoggerFactory.getLogger(DashScopeConfig.class);

    @Value("${anime.llm.api-key:}")
    private String apiKey;

    @Value("${anime.llm.base-url:}")
    private String baseUrl;

    @Value("${anime.llm.model:}")
    private String model;

    private final Vision vision = new Vision();
    private final Image image = new Image();

    @Value("${DASHSCOPE_API_KEY:}")
    private String dashscopeApiKey;

    @Value("${ilink.llm.vision.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String visionBaseUrl;

    @Value("${ilink.llm.vision.model:qwen-vl-plus}")
    private String visionModel;

    @PostConstruct
    public void init() {
        vision.setBaseUrl(visionBaseUrl);
        vision.setModel(visionModel);
        if (dashscopeApiKey != null && !dashscopeApiKey.isBlank()) {
            vision.setApiKey(dashscopeApiKey);
        }
        log.info("DashScopeConfig: baseUrl={}, model={}, visionBaseUrl={}, visionModel={}, visionApiKey={}",
                baseUrl, model, vision.getBaseUrl(), vision.getModel(),
                vision.getApiKey() != null && !vision.getApiKey().isBlank() ? "***set***" : "null");
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Vision getVision() { return vision; }
    public Image getImage() { return image; }

    public static class Vision {
        @Value("${DASHSCOPE_API_KEY:}")
        private String apiKey = "";

        @Value("${VISION_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}")
        private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

        @Value("${ilink.llm.vision.model:qwen-vl-plus}")
        private String model = "qwen-vl-plus";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }

    public static class Image {
        private String baseUrl = "https://ws-bvf49eqmthuay53w.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
        private String defaultResolution = "1024*1024";
        private String defaultStyle = "";
        private String model = "qwen-image-2.0";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getDefaultResolution() { return defaultResolution; }
        public void setDefaultResolution(String defaultResolution) { this.defaultResolution = defaultResolution; }
        public String getDefaultStyle() { return defaultStyle; }
        public void setDefaultStyle(String defaultStyle) { this.defaultStyle = defaultStyle; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }
}
