package com.yunovan.aiadvent.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatCompletionRequest(
        String model,
        List<Message> messages,
        @JsonProperty("max_tokens") Integer maxTokens,
        List<String> stop) {

    public static ChatCompletionRequest of(
            String model, List<Message> messages, Integer maxTokens, List<String> stop) {
        return new ChatCompletionRequest(model, messages, maxTokens, stop);
    }

    public record Message(String role, String content) {
    }
}
