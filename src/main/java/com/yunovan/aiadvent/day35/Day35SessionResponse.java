package com.yunovan.aiadvent.day35;

import java.util.List;

public record Day35SessionResponse(
        String sessionId,
        String task,
        Day35Problem problem,
        List<Day35Problem> problems,
        Day35Solution solution,
        Day35ErrorAnalysis analysis,
        Day35Progress progress,
        boolean finished,
        boolean correct,
        String note,
        String model,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond) {
}
