package com.yunovan.aiadvent.llm;

import java.util.List;

public record CompletionCommand(String prompt, String systemPrompt, Integer maxTokens, List<String> stop) {

    public static CompletionCommand unconstrained(String prompt) {
        return new CompletionCommand(prompt, null, null, null);
    }
}
