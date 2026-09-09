package com.yunovan.aiadvent.day08;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.math.BigDecimal;
import java.util.List;

public record Day08ChatResponse(
        String sessionId,
        String request,
        String content,
        String model,
        int messageCount,
        long elapsedMs,
        long requestTokens,
        long historyTokens,
        long promptTokens,
        long responseTokens,
        Integer realPromptTokens,
        Integer realCompletionTokens,
        Integer realTotalTokens,
        BigDecimal realCostUsd,
        BigDecimal estimatedTurnCostUsd,
        BigDecimal estimatedCumulativeCostUsd,
        long contextLimit,
        boolean exceeded,
        List<ConversationMessage> history) {

    public static Day08ChatResponse exceeded(
            String sessionId,
            String request,
            String content,
            long requestTokens,
            long historyTokens,
            long promptTokens,
            long contextLimit,
            List<ConversationMessage> history) {
        return new Day08ChatResponse(
                sessionId,
                request,
                content,
                null,
                history.size(),
                0L,
                requestTokens,
                historyTokens,
                promptTokens,
                0L,
                null,
                null,
                null,
                null,
                null,
                null,
                contextLimit,
                true,
                history);
    }
}