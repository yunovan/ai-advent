package com.yunovan.aiadvent.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatCompletionResponse(List<Choice> choices, Usage usage) {

    public ChatCompletionResponse(List<Choice> choices) {
        this(choices, null);
    }

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
        Integer promptTokens = usage == null ? null : usage.promptTokens();
        Integer completionTokens = usage == null ? null : usage.completionTokens();
        Integer totalTokens = usage == null ? null : usage.totalTokens();
        BigDecimal costUsd = usage == null ? null : usage.cost();
        return new LlmReply(
                content, choices.getFirst().finishReason(), promptTokens, completionTokens, totalTokens, costUsd, 0L);
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens,
            BigDecimal cost) {
    }
}
