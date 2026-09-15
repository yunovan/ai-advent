package com.yunovan.aiadvent.day12;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day12")
public record Day12Properties(
        Long contextLimit,
        BigDecimal inputPrice,
        BigDecimal outputPrice,
        String dialogDir,
        String profileDir,
        String memoryDir,
        Integer shortTermWindow) {

    public static final long DEFAULT_CONTEXT_LIMIT = 128_000L;
    public static final BigDecimal DEFAULT_INPUT_PRICE = new BigDecimal("0.15");
    public static final BigDecimal DEFAULT_OUTPUT_PRICE = new BigDecimal("0.60");
    public static final String DEFAULT_DIALOG_DIR = "data/day12-dialogs";
    public static final String DEFAULT_PROFILE_DIR = "data/day12-profiles";
    public static final String DEFAULT_MEMORY_DIR = "data/day12-memory";
    public static final int DEFAULT_SHORT_TERM_WINDOW = 10;

    public Day12Properties {
        contextLimit = contextLimit == null || contextLimit <= 0 ? DEFAULT_CONTEXT_LIMIT : contextLimit;
        inputPrice = inputPrice == null || inputPrice.signum() <= 0 ? DEFAULT_INPUT_PRICE : inputPrice;
        outputPrice = outputPrice == null || outputPrice.signum() <= 0 ? DEFAULT_OUTPUT_PRICE : outputPrice;
        dialogDir = valueOr(dialogDir, DEFAULT_DIALOG_DIR);
        profileDir = valueOr(profileDir, DEFAULT_PROFILE_DIR);
        memoryDir = valueOr(memoryDir, DEFAULT_MEMORY_DIR);
        shortTermWindow = shortTermWindow == null || shortTermWindow <= 0
                ? DEFAULT_SHORT_TERM_WINDOW : shortTermWindow;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}