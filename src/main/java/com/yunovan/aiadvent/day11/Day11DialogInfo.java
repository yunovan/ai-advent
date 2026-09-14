package com.yunovan.aiadvent.day11;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.time.Instant;
import java.util.List;

public record Day11DialogInfo(
        String dialogId,
        Instant createdAt,
        Instant finishedAt,
        String summary,
        int messageCount,
        List<ConversationMessage> messages,
        List<Day11MemoryEntry> candidates,
        List<Day11MemoryEntry> working,
        List<Day11MemoryEntry> longTerm) {
}
