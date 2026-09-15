package com.yunovan.aiadvent.day12;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.day11.Day11MemoryEntry;
import java.util.List;

public record Day12ChatResponse(
        String dialogId,
        String request,
        String content,
        String model,
        int messageCount,
        long elapsedMs,
        long contextTokens,
        long requestTokens,
        long profileTokens,
        long workingTokens,
        long longTermTokens,
        long responseTokens,
        long promptTokens,
        long contextLimit,
        boolean exceeded,
        Day12Profile profile,
        List<ConversationMessage> history,
        List<Day11MemoryEntry> working,
        List<Day11MemoryEntry> longTerm) {

    public static Day12ChatResponse exceeded(
            String dialogId,
            String request,
            String content,
            long contextTokens,
            long requestTokens,
            long profileTokens,
            long workingTokens,
            long longTermTokens,
            long promptTokens,
            long contextLimit,
            Day12Profile profile,
            List<ConversationMessage> history,
            List<Day11MemoryEntry> working,
            List<Day11MemoryEntry> longTerm) {
        return new Day12ChatResponse(
                dialogId, request, content, null,
                history.size(), 0L,
                contextTokens, requestTokens,
                profileTokens, workingTokens, longTermTokens,
                0L, promptTokens, contextLimit, true,
                profile, history, working, longTerm);
    }
}