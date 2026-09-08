package com.yunovan.aiadvent.agent;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import org.springframework.stereotype.Component;

@Component
public class ChatAgent implements Agent {

    public static final String SYSTEM_PROMPT =
            "Ты — AI-агент, часть учебного проекта AI Advent. Принимаешь запрос пользователя, отвечаешь кратко и по делу.";

    private final LlmClient llmClient;
    private final LlmProperties properties;

    public ChatAgent(LlmClient llmClient, LlmProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    @Override
    public AgentReply ask(String userRequest) {
        if (userRequest == null || userRequest.isBlank()) {
            throw new IllegalArgumentException("user request must not be blank");
        }
        String trimmed = userRequest.trim();
        LlmReply reply = llmClient.complete(CompletionCommand.unconstrained(trimmed)
                .withSystemPrompt(SYSTEM_PROMPT));
        return new AgentReply(
                reply.content(),
                properties.model(),
                reply.promptTokens(),
                reply.completionTokens(),
                reply.totalTokens(),
                reply.costUsd(),
                reply.elapsedMs());
    }
}