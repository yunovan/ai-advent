package com.yunovan.aiadvent.llm;

import java.util.List;

public record ChatCompletionRequest(String model, List<Message> messages) {

    public static ChatCompletionRequest userPrompt(String model, String prompt) {
        return new ChatCompletionRequest(model, List.of(new Message("user", prompt)));
    }

    public record Message(String role, String content) {
    }
}
