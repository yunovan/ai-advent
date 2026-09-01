package com.yunovan.aiadvent.day02;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import org.springframework.stereotype.Service;

@Service
public class Day02CompareService {

    private final LlmClient llmClient;
    private final LlmProperties properties;

    public Day02CompareService(LlmClient llmClient, LlmProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public CompareResponse compare(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        String trimmed = prompt.trim();

        LlmReply free = llmClient.complete(CompletionCommand.unconstrained(trimmed));
        LlmReply limited = llmClient.complete(new CompletionCommand(
                trimmed,
                Day02Constraints.SYSTEM_PROMPT,
                Day02Constraints.MAX_TOKENS,
                Day02Constraints.STOP));

        CompareSample unconstrained = CompareSample.from(free.content(), free.finishReason());
        CompareSample constrained = CompareSample.from(limited.content(), limited.finishReason());
        return new CompareResponse(
                trimmed,
                properties.model(),
                unconstrained,
                new CompareResponse.ConstrainedSample(
                        constrained.content(),
                        constrained.finishReason(),
                        constrained.characters(),
                        constrained.words(),
                        Day02Constraints.SYSTEM_PROMPT.trim(),
                        Day02Constraints.MAX_TOKENS,
                        Day02Constraints.STOP));
    }
}
