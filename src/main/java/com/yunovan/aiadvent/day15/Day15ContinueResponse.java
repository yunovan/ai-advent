package com.yunovan.aiadvent.day15;

public record Day15ContinueResponse(
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
        Day15TaskState state) {

    public static Day15ContinueResponse exceeded(
            String taskId,
            String request,
            String content,
            long contextTokens,
            long requestTokens,
            long historyTokens,
            long promptTokens,
            long contextLimit,
            Day15TaskState state) {
        return new Day15ContinueResponse(
                taskId, request, content, null, 0L,
                contextTokens, requestTokens, historyTokens, 0L,
                promptTokens, contextLimit, true, state);
    }
}