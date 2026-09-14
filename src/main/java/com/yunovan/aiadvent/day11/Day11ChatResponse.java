package com.yunovan.aiadvent.day11;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.util.List;

public record Day11ChatResponse(
        String dialogId,
        String request,
        String content,
        String model,
        int messageCount,
        long elapsedMs,
        long contextTokens,
        long requestTokens,
        long shortTermTokens,
        long workingTokens,
        long longTermTokens,
        long responseTokens,
        long promptTokens,
        long contextLimit,
        boolean exceeded,
        List<ConversationMessage> history,
        List<Day11MemoryEntry> candidates,
        List<Day11MemoryEntry> working,
        List<Day11MemoryEntry> longTerm) {

    public static Day11ChatResponse exceeded(
            String dialogId,
            String request,
            String content,
            long contextTokens,
            long requestTokens,
            long shortTermTokens,
            long workingTokens,
            long longTermTokens,
            long promptTokens,
            long contextLimit,
            List<ConversationMessage> history,
            List<Day11MemoryEntry> candidates,
            List<Day11MemoryEntry> working,
            List<Day11MemoryEntry> longTerm) {
        return new Day11ChatResponse(
                dialogId, request, content, null,
                history.size(), 0L,
                contextTokens, requestTokens,
                shortTermTokens, workingTokens, longTermTokens,
                0L, promptTokens, contextLimit, true,
                history, candidates, working, longTerm);
    }
}
