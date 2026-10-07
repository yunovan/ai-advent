package com.yunovan.aiadvent.day26;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day26")
public record Day26Properties(
        String endpoint,
        String model,
        Integer connectTimeoutMs,
        Integer readTimeoutMs,
        Double temperature,
        Integer maxTokens) {

    public static final String DEFAULT_ENDPOINT = "http://localhost:11434";
    public static final String DEFAULT_MODEL = "qwen2.5:3b";
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 3_000;
    public static final int DEFAULT_READ_TIMEOUT_MS = 180_000;
    public static final double DEFAULT_TEMPERATURE = 0.2;
    public static final int DEFAULT_MAX_TOKENS = 300;

    public Day26Properties {
        endpoint = valueOr(endpoint, DEFAULT_ENDPOINT);
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        model = valueOr(model, DEFAULT_MODEL);
        connectTimeoutMs = clamp(connectTimeoutMs, 100, 60_000, DEFAULT_CONNECT_TIMEOUT_MS);
        readTimeoutMs = clamp(readTimeoutMs, 1_000, 600_000, DEFAULT_READ_TIMEOUT_MS);
        temperature = temperature == null || temperature.isNaN()
                ? DEFAULT_TEMPERATURE
                : Math.max(0.0, Math.min(2.0, temperature));
        maxTokens = clamp(maxTokens, 1, 4_096, DEFAULT_MAX_TOKENS);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
