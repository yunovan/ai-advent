package com.yunovan.aiadvent.agent;

import com.yunovan.aiadvent.agent.store.ConversationStore;
import com.yunovan.aiadvent.llm.ChatCompletionRequest;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ContextualChatAgent implements ConversationalAgent {

    public static final String DEFAULT_SESSION_ID = "default";

    public static final String SYSTEM_PROMPT =
            "Ты — AI-агент с памятью. Ты помнишь весь предыдущий диалог из этой беседы и отвечаешь, опираясь на него.";

    private final LlmClient llmClient;
    private final LlmProperties properties;
    private final ConversationStore store;

    public ContextualChatAgent(
            LlmClient llmClient, LlmProperties properties, ConversationStore store) {
        this.llmClient = llmClient;
        this.properties = properties;
        this.store = store;
    }

    @Override
    public ConversationReply ask(String sessionId, String userRequest) {
        String sid = normalize(sessionId);
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException("user request must not be blank");
        }
        String trimmed = userRequest.trim();

        Conversation conversation = store.load(sid);
        List<ConversationMessage> messages = new ArrayList<>(conversation.messages());
        if (messages.isEmpty()) {
            messages.add(ConversationMessage.system(SYSTEM_PROMPT));
        }
        messages.add(ConversationMessage.user(trimmed));

        LlmReply reply = llmClient.complete(
                CompletionCommand.unconstrained(trimmed), toChatMessages(messages));

        messages.add(ConversationMessage.assistant(reply.content()));
        store.save(conversation.withMessages(messages));

        return new ConversationReply(
                sid,
                reply.content(),
                properties.model(),
                messages.size(),
                reply.promptTokens(),
                reply.completionTokens(),
                reply.totalTokens(),
                reply.costUsd(),
                reply.elapsedMs(),
                List.copyOf(messages));
    }

    @Override
    public void reset(String sessionId) {
        store.delete(normalize(sessionId));
    }

    public static String normalize(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return DEFAULT_SESSION_ID;
        }
        String cleaned = sessionId.trim().replaceAll("[^A-Za-z0-9_-]", "-");
        return cleaned.isEmpty() ? DEFAULT_SESSION_ID : cleaned;
    }

    private static List<ChatCompletionRequest.Message> toChatMessages(List<ConversationMessage> messages) {
        return messages.stream()
                .map(message -> new ChatCompletionRequest.Message(message.role(), message.content()))
                .toList();
    }
}