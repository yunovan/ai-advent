package com.yunovan.aiadvent.day09;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.DialogMemory;
import java.math.BigDecimal;
import java.util.List;

public record Day09ChatResponse(
        String dialogId,
        String request,
        String content,
        String model,
        int messageCount,
        long elapsedMs,
        boolean compression,
        int historySummaryCount,
        long contextTokens,
        long requestTokens,
        long historyTokens,
        long fullHistoryTokens,
        long historySummaryTokens,
        long promptTokens,
        long fullPromptTokens,
        long savedTokens,
        long responseTokens,
        Integer realPromptTokens,
        Integer realCompletionTokens,
        Integer realTotalTokens,
        BigDecimal realCostUsd,
        BigDecimal estimatedTurnCostUsd,
        BigDecimal estimatedCumulativeCostUsd,
        BigDecimal estimatedFullCumulativeCostUsd,
        long contextLimit,
        boolean exceeded,
        List<ConversationMessage> history,
        List<DialogMemory> memory) {

    public static Day09ChatResponse exceeded(
            String dialogId,
            String request,
            String content,
            boolean compression,
            int historySummaryCount,
            long contextTokens,
            long requestTokens,
            long historyTokens,
            long fullHistoryTokens,
            long historySummaryTokens,
            long promptTokens,
            long fullPromptTokens,
            long savedTokens,
            long contextLimit,
            List<ConversationMessage> history,
            List<DialogMemory> memory) {
        return new Day09ChatResponse(
                dialogId, request, content, null,
                history.size(), 0L,
                compression, historySummaryCount,
                contextTokens, requestTokens,
                historyTokens, fullHistoryTokens, historySummaryTokens,
                promptTokens, fullPromptTokens, savedTokens,
                0L, null, null, null, null, null, null, null,
                contextLimit, true, history, memory);
    }
}