package com.yunovan.aiadvent.day08;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.time.Instant;
import java.util.List;

public record Day08DialogInfo(
        String dialogId,
        Instant createdAt,
        Instant finishedAt,
        String summary,
        int messageCount,
        List<ConversationMessage> history) {
}