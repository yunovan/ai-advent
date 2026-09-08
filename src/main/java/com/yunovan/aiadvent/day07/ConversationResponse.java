package com.yunovan.aiadvent.day07;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.math.BigDecimal;
import java.util.List;

public record ConversationResponse(
        String sessionId,
        String request,
        String content,
        String model,
        int messageCount,
        Integer totalTokens,
        BigDecimal costUsd,
        long elapsedMs,
        List<ConversationMessage> history) {
}