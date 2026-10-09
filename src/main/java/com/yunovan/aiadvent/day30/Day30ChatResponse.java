package com.yunovan.aiadvent.day30;

public record Day30ChatResponse(
        String sessionId,
        int turn,
        String reply,
        int contextMessages,
        int promptChars,
        boolean contextTrimmed,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond,
        int rateRemaining,
        String model) {
}
