package com.yunovan.aiadvent.day27;

public record Day27Message(
        String role,
        String text,
        int turn,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond) {
}
