package com.yunovan.aiadvent.day07;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day7")
public record Day7Properties(String dataDir, Integer maxMessages) {

    public static final String DEFAULT_DATA_DIR = "data/day7-conversations";
    public static final int DEFAULT_MAX_MESSAGES = 40;

    public Day7Properties {
        dataDir = valueOr(dataDir, DEFAULT_DATA_DIR);
        maxMessages = maxMessages == null || maxMessages <= 0 ? DEFAULT_MAX_MESSAGES : maxMessages;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}