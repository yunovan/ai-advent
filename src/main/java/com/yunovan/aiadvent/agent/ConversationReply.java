package com.yunovan.aiadvent.agent;

import java.math.BigDecimal;
import java.util.List;

public record ConversationReply(
        String sessionId,
        String content,
        String model,
        int messageCount,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        BigDecimal costUsd,
        long elapsedMs,
        List<ConversationMessage> messages) {
}