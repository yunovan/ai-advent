package com.yunovan.aiadvent.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public record LlmProperties(String apiKey, String baseUrl, String model) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
