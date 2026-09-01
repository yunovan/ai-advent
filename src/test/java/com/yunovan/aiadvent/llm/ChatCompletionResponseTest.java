package com.yunovan.aiadvent.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChatCompletionResponseTest {

    @Test
    void requiredContentReturnsAssistantMessage() {
        ChatCompletionResponse response = new ChatCompletionResponse(
                List.of(new ChatCompletionResponse.Choice(
                        new ChatCompletionResponse.Message("assistant", "Hello, adventurer"))));

        assertThat(response.requiredContent()).isEqualTo("Hello, adventurer");
    }

    @Test
    void requiredContentRejectsEmptyChoices() {
        assertThatThrownBy(() -> new ChatCompletionResponse(List.of()).requiredContent())
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void requiredContentRejectsBlankMessage() {
        ChatCompletionResponse response = new ChatCompletionResponse(
                List.of(new ChatCompletionResponse.Choice(
                        new ChatCompletionResponse.Message("assistant", "  "))));

        assertThatThrownBy(response::requiredContent)
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("empty");
    }
}
