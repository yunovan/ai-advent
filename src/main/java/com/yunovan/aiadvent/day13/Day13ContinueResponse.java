package com.yunovan.aiadvent.day13;

public record Day13ContinueResponse(
        String taskId,
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
        Day13TaskState state) {

    public static Day13ContinueResponse exceeded(
            String taskId,
            String request,
            String content,
            long contextTokens,
            long requestTokens,
            long historyTokens,
            long promptTokens,
            long contextLimit,
            Day13TaskState state) {
        return new Day13ContinueResponse(
                taskId, request, content, null, 0L,
                contextTokens, requestTokens, historyTokens, 0L,
                promptTokens, contextLimit, true, state);
    }
}