package com.yunovan.aiadvent.day12;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.day11.Day11MemoryEntry;
import java.time.Instant;
import java.util.List;

public record Day12DialogInfo(
        String dialogId,
        Instant createdAt,
        Instant finishedAt,
        String summary,
        int messageCount,
        List<ConversationMessage> messages,
        Day12Profile profile,
        List<Day11MemoryEntry> working,
        List<Day11MemoryEntry> longTerm) {
}