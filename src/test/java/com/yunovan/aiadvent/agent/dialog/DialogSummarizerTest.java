package com.yunovan.aiadvent.agent.dialog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DialogSummarizerTest {

    private final LlmClient llmClient = mock(LlmClient.class);

    private Dialog dialogWithFacts() {
        return new Dialog(
                "d1",
                Instant.now(),
                null,
                null,
                null, 0,
                List.of(
                        ConversationMessage.user("Почему небо синее?"),
                        ConversationMessage.assistant("Из-за рассеяния света Рэлея.")));
    }

    @Test
    void usesLocalFallbackWhenNoApiKey() {
        DialogSummarizer summarizer =
                new DialogSummarizer(llmClient, new LlmProperties(null, "https://openrouter.ai/api/v1", "gpt-4o-mini"));

        String summary = summarizer.summarize(dialogWithFacts());

        assertThat(summary).contains("Почему небо синее?");
        verify(llmClient, never()).complete(any(CompletionCommand.class));
    }

    @Test
    void usesLlmWhenApiKeyPresent() {
        when(llmClient.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Пользователь спрашивал о цвете неба. ", "stop"));
        DialogSummarizer summarizer =
                new DialogSummarizer(llmClient, new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"));

        String summary = summarizer.summarize(dialogWithFacts());

        assertThat(summary).isEqualTo("Пользователь спрашивал о цвете неба.");
    }

    @Test
    void fallsBackToLocalWhenLlmFails() {
        when(llmClient.complete(any(CompletionCommand.class))).thenThrow(new LlmException("LLM API error"));
        DialogSummarizer summarizer =
                new DialogSummarizer(llmClient, new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"));

        String summary = summarizer.summarize(dialogWithFacts());

        assertThat(summary).contains("Почему небо синее?");
    }

    @Test
    void emptyDialogGetsExplicitSummary() {
        DialogSummarizer summarizer =
                new DialogSummarizer(llmClient, new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"));

        assertThat(summarizer.summarize(Dialog.create())).contains("Пустой");
    }
}