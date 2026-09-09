package com.yunovan.aiadvent.day08;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.DialogMemory;
import java.math.BigDecimal;
import java.util.List;

public record Day08ChatResponse(
        String dialogId,
        String request,
        String content,
        String model,
        int messageCount,
        long elapsedMs,
        long contextTokens,
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
        List<ConversationMessage> history,
        List<DialogMemory> memory) {

    public static Day08ChatResponse exceeded(
            String dialogId,
            String request,
            String content,
            long contextTokens,
            long requestTokens,
            long historyTokens,
            long promptTokens,
            long contextLimit,
            List<ConversationMessage> history,
            List<DialogMemory> memory) {
        return new Day08ChatResponse(
                dialogId,
                request,
                content,
                null,
                history.size(),
                0L,
                contextTokens,
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
                history,
                memory);
    }
}