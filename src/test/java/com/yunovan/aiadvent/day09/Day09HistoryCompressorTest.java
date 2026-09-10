package com.yunovan.aiadvent.day09;

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
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class Day09HistoryCompressorTest {

    private final LlmClient llmClient = mock(LlmClient.class);

    @Test
    void localSummaryUsedWithoutApiKey() {
        Day09HistoryCompressor compressor = compressor("");
        when(llmClient.complete(any(CompletionCommand.class))).thenReturn(reply("LLM-сжатие"));

        String summary = compressor.summarizeChunk(List.of(
                ConversationMessage.user("Вопрос про небо"),
                ConversationMessage.assistant("Небо синее")));

        assertThat(summary).contains("Фрагмент из 2 сообщений");
        assertThat(summary).contains("Вопрос про небо");
        verify(llmClient, never()).complete(any(CompletionCommand.class));
    }

    @Test
    void llmSummaryUsedWithApiKey() {
        Day09HistoryCompressor compressor = compressor("secret");
        when(llmClient.complete(any(CompletionCommand.class))).thenReturn(reply("  Сжато: говорили про небо.  "));

        String summary = compressor.summarizeChunk(List.of(
                ConversationMessage.user("Вопрос про небо"),
                ConversationMessage.assistant("Небо синее")));

        assertThat(summary).isEqualTo("Сжато: говорили про небо.");
        ArgumentCaptor<CompletionCommand> captor = ArgumentCaptor.forClass(CompletionCommand.class);
        verify(llmClient).complete(captor.capture());
        assertThat(captor.getValue().prompt()).contains("user: Вопрос про небо");
    }

    @Test
    void fallsBackToLocalWhenLlmThrows() {
        Day09HistoryCompressor compressor = compressor("secret");
        when(llmClient.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("boom"));

        String summary = compressor.summarizeChunk(List.of(ConversationMessage.user("Вопрос")));

        assertThat(summary).contains("Фрагмент из 1 сообщений");
    }

    @Test
    void emptyChunkProducesEmptySummary() {
        Day09HistoryCompressor compressor = compressor("");

        assertThat(compressor.summarizeChunk(List.of())).isEmpty();
        assertThat(compressor.summarizeChunk(null)).isEmpty();
    }

    private Day09HistoryCompressor compressor(String key) {
        return new Day09HistoryCompressor(
                llmClient, new LlmProperties(key, "https://openrouter.ai/api/v1", "gpt-4o-mini"));
    }

    private static LlmReply reply(String content) {
        return new LlmReply(content, "stop", 5, 3, 8, new BigDecimal("0.00001"), 100L);
    }
}