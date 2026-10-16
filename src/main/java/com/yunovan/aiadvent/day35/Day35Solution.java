package com.yunovan.aiadvent.day35;

public record Day35Solution(
        String problemId,
        String answer,
        String explanation,
        String steps,
        String check,
        String model,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond) {
}
