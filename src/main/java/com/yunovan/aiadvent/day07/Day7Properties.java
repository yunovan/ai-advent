package com.yunovan.aiadvent.day07;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day7")
public record Day7Properties(String dialogDir) {

    public static final String DEFAULT_DIALOG_DIR = "data/day7-dialogs";

    public Day7Properties {
        dialogDir = valueOr(dialogDir, DEFAULT_DIALOG_DIR);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}