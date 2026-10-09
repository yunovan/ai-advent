package com.yunovan.aiadvent.day27;

import java.util.List;

public record Day27ChatTurn(
        String sessionId,
        int turn,
        String userMessage,
        String reply,
        String model,
        String endpoint,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond,
        List<Day27Message> history,
        int historySize) {

    public Day27ChatTurn {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
