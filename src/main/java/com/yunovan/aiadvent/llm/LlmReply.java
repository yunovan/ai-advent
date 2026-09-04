package com.yunovan.aiadvent.llm;

import java.math.BigDecimal;

public record LlmReply(
        String content,
        String finishReason,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        BigDecimal costUsd,
        long elapsedMs) {

    public LlmReply(String content, String finishReason) {
        this(content, finishReason, null, null, null, null, 0L);
    }

    public LlmReply withElapsedMs(long elapsedMs) {
        return new LlmReply(
                content, finishReason, promptTokens, completionTokens, totalTokens, costUsd, elapsedMs);
    }
}
