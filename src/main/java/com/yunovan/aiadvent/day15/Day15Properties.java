package com.yunovan.aiadvent.day15;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day15")
public record Day15Properties(
        Long contextLimit,
        BigDecimal inputPrice,
        BigDecimal outputPrice,
        String taskDir,
        Integer shortTermWindow) {

    public static final long DEFAULT_CONTEXT_LIMIT = 128_000L;
    public static final BigDecimal DEFAULT_INPUT_PRICE = new BigDecimal("0.15");
    public static final BigDecimal DEFAULT_OUTPUT_PRICE = new BigDecimal("0.60");
    public static final String DEFAULT_TASK_DIR = "data/day15-tasks";
    public static final int DEFAULT_SHORT_TERM_WINDOW = 10;

    public Day15Properties {
        contextLimit = contextLimit == null || contextLimit <= 0 ? DEFAULT_CONTEXT_LIMIT : contextLimit;
        inputPrice = inputPrice == null || inputPrice.signum() <= 0 ? DEFAULT_INPUT_PRICE : inputPrice;
        outputPrice = outputPrice == null || outputPrice.signum() <= 0 ? DEFAULT_OUTPUT_PRICE : outputPrice;
        taskDir = valueOr(taskDir, DEFAULT_TASK_DIR);
        shortTermWindow = shortTermWindow == null || shortTermWindow <= 0
                ? DEFAULT_SHORT_TERM_WINDOW : shortTermWindow;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}