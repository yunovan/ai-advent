package com.yunovan.aiadvent.day03;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import org.springframework.stereotype.Service;

@Service
public class Day03ReasoningService {

    private final LlmClient llmClient;
    private final LlmProperties properties;

    public Day03ReasoningService(LlmClient llmClient, LlmProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public ReasoningResponse solve(String task) {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        String trimmed = task.trim();

        LlmReply direct = llmClient.complete(CompletionCommand.unconstrained(trimmed));
        LlmReply stepByStep =
                llmClient.complete(CompletionCommand.unconstrained(Day03Prompts.stepByStep(trimmed)));

        LlmReply generated =
                llmClient.complete(CompletionCommand.unconstrained(Day03Prompts.metaPromptRequest(trimmed)));
        String generatedPrompt = generated.content().trim();
        LlmReply metaSolved = llmClient.complete(
                CompletionCommand.unconstrained(Day03Prompts.applyGeneratedPrompt(generatedPrompt, trimmed)));

        LlmReply experts =
                llmClient.complete(CompletionCommand.unconstrained(Day03Prompts.experts(trimmed)));

        return new ReasoningResponse(
                trimmed,
                properties.model(),
                method("direct", "Прямой ответ", trimmed, null, direct),
                method("step-by-step", "Решай пошагово", Day03Prompts.stepByStep(trimmed), null, stepByStep),
                method(
                        "meta-prompt",
                        "Сначала промпт, потом решение",
                        Day03Prompts.applyGeneratedPrompt(generatedPrompt, trimmed),
                        generatedPrompt,
                        metaSolved),
                method("experts", "Группа экспертов", Day03Prompts.experts(trimmed), null, experts));
    }

    private static MethodResult method(
            String id, String title, String sentPrompt, String generatedPrompt, LlmReply reply) {
        return new MethodResult(id, title, sentPrompt, generatedPrompt, reply.content(), reply.finishReason());
    }
}
