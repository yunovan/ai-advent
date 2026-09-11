package com.yunovan.aiadvent.day10;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.time.Instant;
import java.util.List;

public record Day10DialogInfo(
        String dialogId,
        Instant createdAt,
        Instant finishedAt,
        String summary,
        Day10Strategy strategy,
        int windowSize,
        List<Day10Fact> facts,
        List<Day10Branch> branches,
        String activeBranchId,
        int messageCount,
        List<ConversationMessage> messages) {
}