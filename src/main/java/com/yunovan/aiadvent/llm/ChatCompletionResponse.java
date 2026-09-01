package com.yunovan.aiadvent.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(List<Choice> choices) {

    public String requiredContent() {
        return requiredReply().content();
    }

    public LlmReply requiredReply() {
        if (choices == null || choices.isEmpty() || choices.getFirst().message() == null) {
            throw new LlmException("LLM returned an empty response");
        }
        String content = choices.getFirst().message().content();
        if (content == null || content.isBlank()) {
            throw new LlmException("LLM returned an empty response");
        }
        return new LlmReply(content, choices.getFirst().finishReason());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {

        public Choice(Message message) {
            this(message, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String role, String content) {
    }
}
