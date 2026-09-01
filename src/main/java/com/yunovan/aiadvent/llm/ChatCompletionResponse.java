package com.yunovan.aiadvent.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(List<Choice> choices) {

    public String requiredContent() {
        if (choices == null || choices.isEmpty() || choices.getFirst().message() == null) {
            throw new LlmException("LLM returned an empty response");
        }
        String content = choices.getFirst().message().content();
        if (content == null || content.isBlank()) {
            throw new LlmException("LLM returned an empty response");
        }
        return content;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {
    }
}
