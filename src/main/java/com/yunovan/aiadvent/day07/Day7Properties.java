package com.yunovan.aiadvent.day07;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day7")
public record Day7Properties(String dataDir) {

    public static final String DEFAULT_DATA_DIR = "data/day7-conversations";

    public Day7Properties {
        dataDir = valueOr(dataDir, DEFAULT_DATA_DIR);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}