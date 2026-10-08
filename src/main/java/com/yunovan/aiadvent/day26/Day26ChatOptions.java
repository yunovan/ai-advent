package com.yunovan.aiadvent.day26;

public record Day26ChatOptions(double temperature, int maxTokens, Integer numCtx) {

    public static Day26ChatOptions defaults(double temperature, int maxTokens) {
        return new Day26ChatOptions(temperature, maxTokens, null);
    }
}
