package com.yunovan.aiadvent.agent.dialog;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Dialog(
        String id,
        Instant createdAt,
        Instant finishedAt,
        String summary,
        List<ConversationMessage> messages) {

    public static Dialog create() {
        return new Dialog(UUID.randomUUID().toString(), Instant.now(), null, null, List.of());
    }

    public Dialog withMessages(List<ConversationMessage> messages) {
        return new Dialog(id, createdAt, finishedAt, summary, List.copyOf(messages));
    }

    public Dialog finished(String summary, Instant finishedAt) {
        return new Dialog(id, createdAt, finishedAt, summary, messages);
    }

    public boolean isFinished() {
        return finishedAt != null;
    }
}