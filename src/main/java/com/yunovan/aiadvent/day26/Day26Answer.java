package com.yunovan.aiadvent.day26;

public record Day26Answer(
        String prompt,
        String reply,
        String model,
        String endpoint,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond) {
}
