package com.yunovan.aiadvent.day10;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.DialogMemory;
import java.math.BigDecimal;
import java.util.List;

public record Day10ChatResponse(
        String dialogId,
        String request,
        String content,
        String model,
        int messageCount,
        long elapsedMs,
        Day10Strategy strategy,
        int windowSize,
        List<Day10Fact> facts,
        List<Day10Branch> branches,
        String activeBranchId,
        long contextTokens,
        long requestTokens,
        long historyTokens,
        long fullHistoryTokens,
        long promptTokens,
        long fullPromptTokens,
        long savedTokens,
        long responseTokens,
        BigDecimal estimatedTurnCostUsd,
        BigDecimal estimatedCumulativeCostUsd,
        BigDecimal estimatedFullCumulativeCostUsd,
        long contextLimit,
        boolean exceeded,
        List<ConversationMessage> history,
        List<DialogMemory> memory) {

    public static Day10ChatResponse exceeded(
            String dialogId,
            String request,
            String content,
            Day10Strategy strategy,
            int windowSize,
            List<Day10Fact> facts,
            List<Day10Branch> branches,
            String activeBranchId,
            long contextTokens,
            long requestTokens,
            long historyTokens,
            long fullHistoryTokens,
            long promptTokens,
            long fullPromptTokens,
            long savedTokens,
            long contextLimit,
            List<ConversationMessage> history,
            List<DialogMemory> memory) {
        return new Day10ChatResponse(
                dialogId, request, content, null,
                history.size(), 0L,
                strategy, windowSize, facts, branches, activeBranchId,
                contextTokens, requestTokens,
                historyTokens, fullHistoryTokens,
                promptTokens, fullPromptTokens, savedTokens,
                0L, null, null, null,
                contextLimit, true, history, memory);
    }
}