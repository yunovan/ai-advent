package com.yunovan.aiadvent.day08;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day8")
public record Day8Properties(
        Long contextLimit, BigDecimal inputPrice, BigDecimal outputPrice, String dialogDir) {

    public static final long DEFAULT_CONTEXT_LIMIT = 128_000L;
    public static final BigDecimal DEFAULT_INPUT_PRICE = new BigDecimal("0.15");
    public static final BigDecimal DEFAULT_OUTPUT_PRICE = new BigDecimal("0.60");
    public static final String DEFAULT_DIALOG_DIR = "data/day8-dialogs";

    public Day8Properties {
        contextLimit = contextLimit == null || contextLimit <= 0 ? DEFAULT_CONTEXT_LIMIT : contextLimit;
        inputPrice = inputPrice == null || inputPrice.signum() <= 0 ? DEFAULT_INPUT_PRICE : inputPrice;
        outputPrice = outputPrice == null || outputPrice.signum() <= 0 ? DEFAULT_OUTPUT_PRICE : outputPrice;
        dialogDir = valueOr(dialogDir, DEFAULT_DIALOG_DIR);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}