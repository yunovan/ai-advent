package com.yunovan.aiadvent.day09;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day9")
public record Day9Properties(
        Long contextLimit,
        BigDecimal inputPrice,
        BigDecimal outputPrice,
        String dialogDir,
        int recentMessages,
        int chunkSize) {

    public static final long DEFAULT_CONTEXT_LIMIT = 128_000L;
    public static final BigDecimal DEFAULT_INPUT_PRICE = new BigDecimal("0.15");
    public static final BigDecimal DEFAULT_OUTPUT_PRICE = new BigDecimal("0.60");
    public static final String DEFAULT_DIALOG_DIR = "data/day9-dialogs";
    public static final int DEFAULT_RECENT = 10;
    public static final int DEFAULT_CHUNK = 10;

    public Day9Properties {
        contextLimit = contextLimit == null || contextLimit <= 0 ? DEFAULT_CONTEXT_LIMIT : contextLimit;
        inputPrice = inputPrice == null || inputPrice.signum() <= 0 ? DEFAULT_INPUT_PRICE : inputPrice;
        outputPrice = outputPrice == null || outputPrice.signum() <= 0 ? DEFAULT_OUTPUT_PRICE : outputPrice;
        dialogDir = valueOr(dialogDir, DEFAULT_DIALOG_DIR);
        recentMessages = recentMessages <= 0 ? DEFAULT_RECENT : recentMessages;
        chunkSize = chunkSize <= 0 ? DEFAULT_CHUNK : chunkSize;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}