package com.yunovan.aiadvent.day22;

import com.yunovan.aiadvent.day21.Day21Properties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day22")
public record Day22Properties(
        String strategy,
        Integer topK,
        Integer answerMaxTokens) {

    public static final String DEFAULT_STRATEGY = Day21Properties.STRATEGY_FIXED;
    public static final int DEFAULT_TOP_K = 5;
    public static final int DEFAULT_ANSWER_MAX_TOKENS = 400;

    public Day22Properties {
        strategy = valueOr(strategy, DEFAULT_STRATEGY);
        topK = topK == null || topK <= 0 ? DEFAULT_TOP_K : topK;
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
}