package com.yunovan.aiadvent.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class LlmIoErrorsTest {

    @Test
    void describeSkipsNullMessagesAndShowsCauseType() {
        IOException cause = new IOException();
        ResourceAccessException error =
                new ResourceAccessException("I/O error on POST request for \"https://openrouter.ai/api/v1/chat/completions\": null", cause);

        String description = LlmIoErrors.describe(error);

        assertThat(description).contains("ResourceAccessException");
        assertThat(description).contains("IOException");
        assertThat(description).contains("openrouter.ai");
        assertThat(description).doesNotEndWith(": null");
    }

    @Test
    void httpErrorsAreNotRetryable() {
        assertThat(LlmIoErrors.isRetryable(new IOException())).isTrue();
        assertThat(LlmIoErrors.isRetryable(new ResourceAccessException("io"))).isTrue();
    }
}
