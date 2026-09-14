package com.yunovan.aiadvent.day10;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.util.List;

public record Day10Branch(
        String id,
        String name,
        String parentId,
        int checkpointAt,
        List<ConversationMessage> messages) {

    public static Day10Branch main() {
        return new Day10Branch("main", "Основная", null, 0, List.of());
    }

    public static Day10Branch fork(String id, String name, Day10Branch parent, int checkpointAt) {
        int end = Math.min(checkpointAt, parent.messages().size());
        return new Day10Branch(id, name, parent.id(), checkpointAt, List.copyOf(parent.messages().subList(0, end)));
    }

    public Day10Branch withMessages(List<ConversationMessage> newMessages) {
        return new Day10Branch(id, name, parentId, checkpointAt, List.copyOf(newMessages));
    }

    public int messageCount() {
        return messages == null ? 0 : messages.size();
    }
}