package com.yunovan.aiadvent.day09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.Dialog;
import com.yunovan.aiadvent.agent.dialog.DialogContext;
import com.yunovan.aiadvent.agent.dialog.DialogNotFoundException;
import com.yunovan.aiadvent.agent.dialog.DialogStore;
import com.yunovan.aiadvent.agent.dialog.DialogSummarizer;
import com.yunovan.aiadvent.agent.dialog.FileDialogStore;
import com.yunovan.aiadvent.day08.TokenEstimator;
import com.yunovan.aiadvent.llm.ChatCompletionRequest;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class Day09DialogServiceTest {

    @TempDir
    Path tempDir;

    private static final BigDecimal PRICE = new BigDecimal("0.15");

    private final LlmClient llmClient = mock(LlmClient.class);
    private final DialogSummarizer summarizer = mock(DialogSummarizer.class);
    private final Day09HistoryCompressor compressor = mock(Day09HistoryCompressor.class);
    private final TokenEstimator estimator = new TokenEstimator();

    private FileDialogStore store;
    private Day09DialogService service;
    private Dialog current;

    private void setup(long contextLimit, int recent, int chunk) {
        when(compressor.summarizeChunk(any())).thenReturn("Сжатый фрагмент истории.");
        store = new FileDialogStore(tempDir);
        service = new Day09DialogService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day9Properties(contextLimit, PRICE, new BigDecimal("0.60"), "data/day9-dialogs", recent, chunk),
                store,
                new DialogContext(),
                summarizer,
                compressor,
                estimator);
        current = store.create();
    }

    private void setup(long contextLimit) {
        setup(contextLimit, 10, 10);
    }

    @Test
    void chatCountsTokensForRequestHistoryAndResponse() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Привет, Ася!"));
        setup(128_000L);

        Day09ChatResponse response = service.chat(current.id(), "Привет, меня зовут Ася", null, null);

        assertThat(response.exceeded()).isFalse();
        assertThat(response.compression()).isTrue();
        assertThat(response.historySummaryCount()).isZero();
        assertThat(response.contextTokens())
                .isEqualTo(estimator.estimate(new DialogContext().systemPrompt(List.of())));
        assertThat(response.requestTokens()).isEqualTo(estimator.estimate("Привет, меня зовут Ася"));
        assertThat(response.historyTokens()).isZero();
        assertThat(response.responseTokens()).isEqualTo(estimator.estimate("Привет, Ася!"));
        assertThat(response.promptTokens()).isPositive();
        assertThat(response.savedTokens()).isZero();
        assertThat(response.messageCount()).isEqualTo(2);
    }

    @Test
    void compressionFoldsOlderChunksIntoBannerAfterThreshold() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Короткий ответ"));
        setup(128_000L, 2, 2);

        service.chat(current.id(), "Первый вопрос", null, null);
        service.chat(current.id(), "Второй вопрос", null, null);
        Day09ChatResponse third = service.chat(current.id(), "Третий вопрос", null, null);

        assertThat(third.historySummaryCount()).isEqualTo(2);
        assertThat(third.historyTokens()).isLessThan(third.fullHistoryTokens());

        ArgumentCaptor<List<ChatCompletionRequest.Message>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(3)).complete(any(CompletionCommand.class), messagesCaptor.capture());
        assertThat(messagesCaptor.getAllValues().get(2).getFirst().content()).contains("Сжатая история");
        assertThat(messagesCaptor.getAllValues().get(2)).hasSize(3);
        assertThat(messagesCaptor.getAllValues().get(2).getLast().role()).isEqualTo("assistant");

        verify(compressor, atLeastOnce()).summarizeChunk(any());
    }

    @Test
    void compressionSavesTokensAsDialogGrowsPastSeveralChunks() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply(
                "Короткий ответ, в котором агент очень коротко и ёмко формулирует суть того, что он услышал"));
        setup(128_000L, 2, 2);

        Day09ChatResponse last = null;
        for (int i = 1; i <= 8; i++) {
            last = service.chat(current.id(), "Очень длинный и подробный вопрос номер " + i
                    + " про то, как устроено сжатие истории в длинных диалогах с большим контекстом", null, null);
        }

        assertThat(last.historySummaryCount()).isGreaterThan(2);
        assertThat(last.savedTokens()).isPositive();
        assertThat(last.historyTokens()).isLessThan(last.fullHistoryTokens());
        assertThat(last.promptTokens()).isLessThan(last.fullPromptTokens());
    }

    @Test
    void compressionOffSendsFullHistoryAndSavesNothing() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Короткий ответ"));
        setup(128_000L, 2, 2);

        service.chat(current.id(), "Первый вопрос", null, true);
        service.chat(current.id(), "Второй вопрос", null, false);

        Day09ChatResponse third = service.chat(current.id(), "Третий вопрос", null, false);

        assertThat(third.savedTokens()).isZero();
        assertThat(third.compression()).isFalse();
        assertThat(third.historyTokens()).isEqualTo(third.fullHistoryTokens());

        ArgumentCaptor<List<ChatCompletionRequest.Message>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(3)).complete(any(CompletionCommand.class), messagesCaptor.capture());
        assertThat(messagesCaptor.getAllValues().get(2).getFirst().content()).doesNotContain("Сжатая история");
        assertThat(messagesCaptor.getAllValues().get(2)).hasSize(5);
    }

    @Test
    void longDialogExceedingLimitIsBlockedBeforeCallingLlm() {
        setup(60L);

        Day09ChatResponse exceeded = service.chat(
                current.id(), "Очень длинное сообщение, которое превысит крошечный лимит модели", null, null);

        assertThat(exceeded.exceeded()).isTrue();
        assertThat(exceeded.promptTokens()).isGreaterThan(60L);
        assertThat(exceeded.content()).contains("превысил контекстное окно");
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void metricsReportShowsCompressionSavingsAfterThreshold() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Короткий ответ"));
        setup(128_000L, 2, 2);

        for (int i = 1; i <= 8; i++) {
            service.chat(current.id(), "Длинный и подробный вопрос номер " + i
                    + " про то, как устроено сжатие истории в длинных диалогах", null, null);
        }

        Day09GrowthReport report = service.metrics(current.id());

        assertThat(report.turns()).hasSize(8);
        assertThat(report.totalTokens()).isLessThan(report.fullTotalTokens());
        assertThat(report.savedTokens()).isPositive();
        Day09GrowthTurn last = report.turns().get(7);
        assertThat(last.promptTokens()).isLessThan(last.fullPromptTokens());
        assertThat(last.cumulativeTokens()).isLessThan(last.cumulativeFullTokens());
        assertThat(report.totalCostUsd()).isPositive();
    }

    @Test
    void chatAfterFinishIsRejected() {
        when(summarizer.summarize(any())).thenReturn("Итог диалога");
        setup(128_000L);
        service.finish(current.id());

        assertThatThrownBy(() -> service.chat(current.id(), "Вопрос после завершения", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("завершён");
    }

    @Test
    void chatOnUnknownDialogThrowsNotFound() {
        setup(128_000L);

        assertThatThrownBy(() -> service.chat("nope", "вопрос", null, null))
                .isInstanceOf(DialogNotFoundException.class);
    }

    @Test
    void chatRejectsBlankRequest() {
        setup(128_000L);

        assertThatThrownBy(() -> service.chat(current.id(), "   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void finishMarksDialogFinishedAndPersistsSummary() {
        when(summarizer.summarize(any())).thenReturn("Итог: говорили про небо.");
        setup(128_000L);

        Day09FinishResponse response = service.finish(current.id());

        assertThat(response.summary()).isEqualTo("Итог: говорили про небо.");
        assertThat(response.finishedAt()).isNotNull();
        assertThat(store.load(current.id()).isFinished()).isTrue();
    }

    @Test
    void dialogInfoExposesHistorySummaryState() {
        when(summarizer.summarize(any())).thenReturn("Итог дня");
        setup(128_000L);

        Day09DialogInfo info = service.get(current.id());

        assertThat(info.dialogId()).isEqualTo(current.id());
        assertThat(info.historySummary()).isNull();
        assertThat(info.messageCount()).isZero();
    }

    private static LlmReply reply(String content) {
        return new LlmReply(content, "stop", 10, 7, 17, new BigDecimal("0.00001"), 100L);
    }
}