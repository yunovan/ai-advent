package com.yunovan.aiadvent.day09;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.time.Instant;
import java.util.List;

public record Day09DialogInfo(
        String dialogId,
        Instant createdAt,
        Instant finishedAt,
        String summary,
        String historySummary,
        int historySummaryCount,
        int messageCount,
        List<ConversationMessage> history) {
}