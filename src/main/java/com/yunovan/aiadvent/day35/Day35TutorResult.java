package com.yunovan.aiadvent.day35;

public record Day35TutorResult(
        Day35Solution solution,
        Day35ErrorAnalysis analysis,
        String model,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond) {
}
