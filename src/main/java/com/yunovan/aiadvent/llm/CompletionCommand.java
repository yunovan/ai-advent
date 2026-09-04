package com.yunovan.aiadvent.llm;

import java.util.List;

public record CompletionCommand(
        String prompt,
        String systemPrompt,
        Integer maxTokens,
        List<String> stop,
        Double temperature,
        String model) {

    public CompletionCommand(String prompt, String systemPrompt, Integer maxTokens, List<String> stop) {
        this(prompt, systemPrompt, maxTokens, stop, null, null);
    }

    public CompletionCommand(
            String prompt, String systemPrompt, Integer maxTokens, List<String> stop, Double temperature) {
        this(prompt, systemPrompt, maxTokens, stop, temperature, null);
    }

    public static CompletionCommand unconstrained(String prompt) {
        return new CompletionCommand(prompt, null, null, null, null, null);
    }

    public static CompletionCommand withTemperature(String prompt, double temperature) {
        return new CompletionCommand(prompt, null, null, null, temperature, null);
    }

    public static CompletionCommand withModel(String prompt, String model) {
        return new CompletionCommand(prompt, null, null, null, null, model);
    }
}
