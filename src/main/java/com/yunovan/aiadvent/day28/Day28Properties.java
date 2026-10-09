package com.yunovan.aiadvent.day28;

import com.yunovan.aiadvent.day21.Day21Properties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day28")
public record Day28Properties(
        String strategy,
        Integer topKBefore,
        Integer topKAfter,
        Double threshold,
        Boolean rewrite,
        Integer answerMaxTokens,
        Integer evaluateRuns) {

    public static final String DEFAULT_STRATEGY = Day21Properties.STRATEGY_FIXED;
    public static final int DEFAULT_TOP_K_BEFORE = 10;
    public static final int DEFAULT_TOP_K_AFTER = 5;
    public static final double DEFAULT_THRESHOLD = 0.5;
    public static final boolean DEFAULT_REWRITE = true;
    public static final int DEFAULT_ANSWER_MAX_TOKENS = 400;
    public static final int DEFAULT_EVALUATE_RUNS = 2;

    public Day28Properties {
        strategy = valueOr(strategy, DEFAULT_STRATEGY);
        topKBefore = topKBefore == null || topKBefore <= 0 ? DEFAULT_TOP_K_BEFORE : topKBefore;
        topKAfter = topKAfter == null || topKAfter <= 0 ? DEFAULT_TOP_K_AFTER : topKAfter;
        threshold = threshold == null || threshold.isNaN() ? DEFAULT_THRESHOLD
                : Math.max(0.0, Math.min(1.0, threshold));
        rewrite = rewrite == null ? DEFAULT_REWRITE : rewrite;
        answerMaxTokens = answerMaxTokens == null || answerMaxTokens <= 0
                ? DEFAULT_ANSWER_MAX_TOKENS : answerMaxTokens;
        evaluateRuns = evaluateRuns == null || evaluateRuns <= 0 ? DEFAULT_EVALUATE_RUNS : evaluateRuns;
    }

    public String normalizedStrategy() {
        return strategy.equals(Day21Properties.STRATEGY_STRUCTURAL)
                ? Day21Properties.STRATEGY_STRUCTURAL : Day21Properties.STRATEGY_FIXED;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}