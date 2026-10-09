package com.yunovan.aiadvent.day30;

public record Day30StressItem(
        int index,
        boolean ok,
        boolean rateLimited,
        long latencyMs,
        int outputTokens,
        String error) {
}
