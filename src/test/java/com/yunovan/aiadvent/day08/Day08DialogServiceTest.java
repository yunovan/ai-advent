package com.yunovan.aiadvent.day08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

class Day08DialogServiceTest {

    @TempDir
    Path tempDir;

    private static final BigDecimal PRICE = new BigDecimal("0.15");

    private final LlmClient llmClient = mock(LlmClient.class);
    private final DialogSummarizer summarizer = mock(DialogSummarizer.class);
    private final TokenEstimator estimator = new TokenEstimator();

    private FileDialogStore store;
    private Day08DialogService service;
    private Dialog current;

    private void setup(long contextLimit) {
        store = new FileDialogStore(tempDir);
        service = new Day08DialogService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day8Properties(contextLimit, PRICE, new BigDecimal("0.60"), "data/day8-dialogs"),
                store,
                new DialogContext(),
                summarizer,
                estimator);
        current = store.create();
    }

    @Test
    void chatCountsTokensForRequestHistoryAndResponse() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Привет, Ася!"));
        setup(128_000L);
        String request = "Привет, меня зовут Ася";

        Day08ChatResponse response = service.chat(current.id(), request, null);

        assertThat(response.exceeded()).isFalse();
        assertThat(response.dialogId()).isEqualTo(current.id());
        assertThat(response.contextTokens())
                .isEqualTo(estimator.estimate(new DialogContext().systemPrompt(List.of())));
        assertThat(response.requestTokens()).isEqualTo(estimator.estimate(request));
        assertThat(response.historyTokens()).isZero();
        assertThat(response.responseTokens()).isEqualTo(estimator.estimate("Привет, Ася!"));
        assertThat(response.promptTokens()).isPositive();
        assertThat(response.realTotalTokens()).isEqualTo(17);
        assertThat(response.messageCount()).isEqualTo(2);
        assertThat(response.estimatedTurnCostUsd()).isNotNull();
        assertThat(response.estimatedCumulativeCostUsd()).isNotNull();
    }

    @Test
    void memoryOfPreviousDialogIsIncludedInPromptAndCost() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Вы спрашивали про небо."));
        setup(128_000L);
        Dialog past = Dialog.create().finished("Говорили о синем небе.", Instant.now().minusSeconds(60));
        store.save(past);

        Day08ChatResponse response = service.chat(current.id(), "О чём мы говорили раньше?", null);

        ArgumentCaptor<List<ChatCompletionRequest.Message>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(llmClient).complete(any(CompletionCommand.class), messagesCaptor.capture());
        assertThat(messagesCaptor.getValue().getFirst().role()).isEqualTo("system");
        assertThat(messagesCaptor.getValue().getFirst().content()).contains("Говорили о синем небе.");
        assertThat(response.memory()).hasSize(1);
        assertThat(response.memory().getFirst().summary()).isEqualTo("Говорили о синем небе.");
        assertThat(response.contextTokens()).isPositive();
    }

    @Test
    void historyTokensAndCumulativeCostGrowAcrossAsks() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Ответ без истории"));
        setup(128_000L);

        Day08ChatResponse first = service.chat(current.id(), "Первый вопрос, довольно длинный и подробный", null);
        Day08ChatResponse second = service.chat(current.id(), "Второй вопрос, ещё более длинный", null);
        Day08ChatResponse third = service.chat(current.id(), "Третий", null);

        assertThat(second.historyTokens()).isPositive();
        assertThat(third.historyTokens()).isGreaterThan(second.historyTokens());
        assertThat(third.promptTokens()).isGreaterThan(second.promptTokens());
        assertThat(third.estimatedCumulativeCostUsd())
                .isGreaterThan(second.estimatedCumulativeCostUsd());
        assertThat(first.estimatedCumulativeCostUsd()).isPositive();
    }

    @Test
    void longDialogExceedingLimitIsBlockedBeforeCallingLlm() {
        setup(60L);

        Day08ChatResponse exceeded = service.chat(
                current.id(), "Очень длинное сообщение, которое превысит крошечный лимит модели", null);

        assertThat(exceeded.exceeded()).isTrue();
        assertThat(exceeded.promptTokens()).isGreaterThan(60L);
        assertThat(exceeded.content()).contains("превысил контекстное окно");
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void dialogGrowsUntilItHitsTheLimitThenStopsCallingLlm() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Короткий ответ"));
        String ask1 = "Привет, как дела?";
        String ask2 = "Расскажи подробнее про токены";
        String ask3 = "Это очень длинное сообщение, которое надо написать максимально пространно, "
                + "чтобы гарантированно переполнить контекстное окно, заданное в этом тесте";
        long context = estimator.estimate(new DialogContext().systemPrompt(List.of()));
        long firstTurn = estimator.estimate(ask1) + estimator.estimate("Короткий ответ");
        long secondTurn = estimator.estimate(ask2) + estimator.estimate("Короткий ответ");
        long prompt2 = context + firstTurn + estimator.estimate(ask2);
        long prompt3 = context + firstTurn + secondTurn + estimator.estimate(ask3);
        long limit = prompt2 + (prompt3 - prompt2) / 2;
        setup(limit);

        Day08ChatResponse first = service.chat(current.id(), ask1, null);
        Day08ChatResponse second = service.chat(current.id(), ask2, null);
        Day08ChatResponse blocked = service.chat(current.id(), ask3, null);

        assertThat(first.exceeded()).isFalse();
        assertThat(second.exceeded()).isFalse();
        assertThat(blocked.exceeded()).isTrue();
        assertThat(blocked.promptTokens()).isGreaterThan(limit);
        assertThat(blocked.history()).hasSize(4);
        verify(llmClient, times(2)).complete(any(CompletionCommand.class), any());
    }

    @Test
    void metricsReportShowsGrowingTurnsAndCumulativeCost() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Короткий ответ здесь"));
        setup(128_000L);

        service.chat(current.id(), "Один вопрос, не очень длинный", null);
        service.chat(current.id(), "Второй вопрос, чуть длиннее первого", null);

        Day08GrowthReport report = service.metrics(current.id());

        assertThat(report.turns()).hasSize(2);
        Day08GrowthTurn second = report.turns().get(1);
        assertThat(second.promptTokens()).isGreaterThan(report.turns().get(0).promptTokens());
        assertThat(second.cumulativeTokens()).isGreaterThan(report.turns().get(0).cumulativeTokens());
        assertThat(second.cumulativeCostUsd()).isGreaterThan(report.turns().get(0).cumulativeCostUsd());
        assertThat(report.totalTokens()).isPositive();
        assertThat(report.totalCostUsd()).isPositive();
    }

    @Test
    void chatAfterFinishIsRejected() {
        when(summarizer.summarize(any())).thenReturn("Итог диалога");
        setup(128_000L);
        service.finish(current.id());

        assertThatThrownBy(() -> service.chat(current.id(), "Вопрос после завершения", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("завершён");
    }

    @Test
    void chatOnUnknownDialogThrowsNotFound() {
        setup(128_000L);

        assertThatThrownBy(() -> service.chat("nope", "вопрос", null))
                .isInstanceOf(DialogNotFoundException.class);
    }

    @Test
    void chatRejectsBlankRequest() {
        setup(128_000L);

        assertThatThrownBy(() -> service.chat(current.id(), "   ", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void finishMarksDialogFinishedAndPersistsSummary() {
        when(summarizer.summarize(any())).thenReturn("Итог: говорили про небо.");
        setup(128_000L);

        Day08FinishResponse response = service.finish(current.id());

        assertThat(response.summary()).isEqualTo("Итог: говорили про небо.");
        assertThat(response.finishedAt()).isNotNull();
        assertThat(store.load(current.id()).isFinished()).isTrue();
    }

    private static LlmReply reply(String content) {
        return new LlmReply(content, "stop", 10, 7, 17, new BigDecimal("0.00001"), 100L);
    }
}