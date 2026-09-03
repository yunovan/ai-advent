package com.yunovan.aiadvent.llm;

import java.util.List;

public record CompletionCommand(
        String prompt, String systemPrompt, Integer maxTokens, List<String> stop, Double temperature) {

    public CompletionCommand(String prompt, String systemPrompt, Integer maxTokens, List<String> stop) {
        this(prompt, systemPrompt, maxTokens, stop, null);
    }

    public static CompletionCommand unconstrained(String prompt) {
        return new CompletionCommand(prompt, null, null, null, null);
    }

    public static CompletionCommand withTemperature(String prompt, double temperature) {
        return new CompletionCommand(prompt, null, null, null, temperature);
    }
}
