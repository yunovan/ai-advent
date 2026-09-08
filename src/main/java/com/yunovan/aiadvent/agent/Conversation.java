package com.yunovan.aiadvent.agent;

import java.time.Instant;
import java.util.List;

public record Conversation(String sessionId, Instant createdAt, List<ConversationMessage> messages) {

    public static Conversation empty(String sessionId) {
        return new Conversation(sessionId, Instant.now(), List.of());
    }

    public Conversation withMessages(List<ConversationMessage> messages) {
        return new Conversation(sessionId, createdAt, List.copyOf(messages));
    }
}