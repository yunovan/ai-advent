package com.yunovan.aiadvent.day24;

import com.yunovan.aiadvent.day21.Day21Properties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day24")
public record Day24Properties(
        String strategy,
        Integer topKBefore,
        Integer topKAfter,
        Double threshold,
        Double unknownThreshold,
        Boolean rewrite,
        Integer quotesPerSource,
        Integer quoteMinChars,
        Double supportThreshold,
        Integer answerMaxTokens) {

    public static final String DEFAULT_STRATEGY = Day21Properties.STRATEGY_FIXED;
    public static final int DEFAULT_TOP_K_BEFORE = 10;
    public static final int DEFAULT_TOP_K_AFTER = 5;
    public static final double DEFAULT_THRESHOLD = 0.5;
    public static final double DEFAULT_UNKNOWN_THRESHOLD = 0.32;
    public static final boolean DEFAULT_REWRITE = true;
    public static final int DEFAULT_QUOTES_PER_SOURCE = 3;
    public static final int DEFAULT_QUOTE_MIN_CHARS = 24;
    public static final double DEFAULT_SUPPORT_THRESHOLD = 0.35;
    public static final int DEFAULT_ANSWER_MAX_TOKENS = 400;

    public Day24Properties {
        strategy = valueOr(strategy, DEFAULT_STRATEGY);
        topKBefore = clamp(topKBefore, 1, 20, DEFAULT_TOP_K_BEFORE);
        topKAfter = clamp(topKAfter, 1, 10, DEFAULT_TOP_K_AFTER);
        topKAfter = Math.min(topKBefore, topKAfter);
        threshold = threshold == null || threshold.isNaN()
                ? DEFAULT_THRESHOLD : Math.max(0.0, Math.min(1.0, threshold));
        unknownThreshold = unknownThreshold == null || unknownThreshold.isNaN()
                ? DEFAULT_UNKNOWN_THRESHOLD
                : Math.max(0.0, Math.min(1.0, unknownThreshold));
        rewrite = rewrite == null ? DEFAULT_REWRITE : rewrite;
        quotesPerSource = quotesPerSource == null || quotesPerSource <= 0
                ? DEFAULT_QUOTES_PER_SOURCE : quotesPerSource;
        quoteMinChars = quoteMinChars == null || quoteMinChars <= 0
                ? DEFAULT_QUOTE_MIN_CHARS : quoteMinChars;
        supportThreshold = supportThreshold == null || supportThreshold.isNaN()
                ? DEFAULT_SUPPORT_THRESHOLD
                : Math.max(0.0, Math.min(1.0, supportThreshold));
        answerMaxTokens = answerMaxTokens == null || answerMaxTokens <= 0
                ? DEFAULT_ANSWER_MAX_TOKENS : answerMaxTokens;
    }

    public String normalizedStrategy() {
        return strategy.equals(Day21Properties.STRATEGY_STRUCTURAL)
                ? Day21Properties.STRATEGY_STRUCTURAL : Day21Properties.STRATEGY_FIXED;
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