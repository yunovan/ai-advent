package com.yunovan.aiadvent.day14;

import java.util.List;

public record Day14AdviseResponse(
        String requestId,
        String request,
        String content,
        String model,
        long elapsedMs,
        long contextTokens,
        long requestTokens,
        long historyTokens,
        long responseTokens,
        long promptTokens,
        long contextLimit,
        boolean exceeded,
        List<Day14Invariant> invariants) {

    public static Day14AdviseResponse exceeded(
            String requestId,
            String request,
            String content,
            long contextTokens,
            long requestTokens,
            long historyTokens,
            long promptTokens,
            long contextLimit,
            List<Day14Invariant> invariants) {
        return new Day14AdviseResponse(
                requestId, request, content, null, 0L,
                contextTokens, requestTokens, historyTokens, 0L,
                promptTokens, contextLimit, true, invariants);
    }
}