package com.yunovan.aiadvent.day05;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day5")
public record Day5Properties(String weakModel, String mediumModel, String strongModel) {

    public Day5Properties {
        weakModel = blankToDefault(weakModel, Day5Models.WEAK_MODEL);
        mediumModel = blankToDefault(mediumModel, Day5Models.MEDIUM_MODEL);
        strongModel = blankToDefault(strongModel, Day5Models.STRONG_MODEL);
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
