package com.yunovan.aiadvent.day30;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day30")
public record Day30Properties(
        String host,
        Integer port,
        String apiKey,
        String systemPrompt,
        Integer maxMessages,
        Integer maxPromptChars,
        Integer rateLimitPerMinute,
        Integer maxConcurrent,
        Integer maxSessions,
        Integer stressRequests,
        Integer stressConcurrency,
        Double temperature,
        Integer maxTokens) {

    public static final String DEFAULT_HOST = "localhost";
    public static final int DEFAULT_PORT = 8080;
    public static final String DEFAULT_SYSTEM_PROMPT = "Ты — приватный ассистент проекта AI Advent, "
            + "работающий на локальной модели без выхода в интернет. Отвечай по-русски, кратко и по "
            + "делу, помни контекст диалога и учитывай предыдущие сообщения. Не выдумывай факты.";
    public static final int DEFAULT_MAX_MESSAGES = 12;
    public static final int DEFAULT_MAX_PROMPT_CHARS = 6000;
    public static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 10;
    public static final int DEFAULT_MAX_CONCURRENT = 4;
    public static final int DEFAULT_MAX_SESSIONS = 50;
    public static final int DEFAULT_STRESS_REQUESTS = 6;
    public static final int DEFAULT_STRESS_CONCURRENCY = 3;
    public static final double DEFAULT_TEMPERATURE = 0.2;
    public static final int DEFAULT_MAX_TOKENS = 300;

    public Day30Properties {
        host = valueOr(host, DEFAULT_HOST);
        port = clamp(port, 1, 65_535, DEFAULT_PORT);
        apiKey = apiKey == null ? "" : apiKey.trim();
        systemPrompt = valueOr(systemPrompt, DEFAULT_SYSTEM_PROMPT);
        maxMessages = clamp(maxMessages, 2, 200, DEFAULT_MAX_MESSAGES);
        maxPromptChars = clamp(maxPromptChars, 200, 200_000, DEFAULT_MAX_PROMPT_CHARS);
        rateLimitPerMinute = clamp(rateLimitPerMinute, 1, 100_000, DEFAULT_RATE_LIMIT_PER_MINUTE);
        maxConcurrent = clamp(maxConcurrent, 1, 1_000, DEFAULT_MAX_CONCURRENT);
        maxSessions = clamp(maxSessions, 1, 10_000, DEFAULT_MAX_SESSIONS);
        stressRequests = clamp(stressRequests, 1, 100, DEFAULT_STRESS_REQUESTS);
        stressConcurrency = clamp(stressConcurrency, 1, 16, DEFAULT_STRESS_CONCURRENCY);
        temperature = temperature == null || temperature.isNaN()
                ? DEFAULT_TEMPERATURE : Math.max(0.0, Math.min(2.0, temperature));
        maxTokens = clamp(maxTokens, 1, 4_096, DEFAULT_MAX_TOKENS);
    }

    public boolean apiKeyRequired() {
        return !apiKey.isBlank();
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
