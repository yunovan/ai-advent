package com.yunovan.aiadvent.day11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.agent.dialog.Dialog;
import com.yunovan.aiadvent.agent.dialog.DialogContext;
import com.yunovan.aiadvent.agent.dialog.DialogNotFoundException;
import com.yunovan.aiadvent.agent.dialog.DialogSummarizer;
import com.yunovan.aiadvent.day08.TokenEstimator;
import com.yunovan.aiadvent.llm.ChatCompletionRequest;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class Day11DialogServiceTest {

    @TempDir
    Path tempDir;

    private static final BigDecimal PRICE = new BigDecimal("0.15");
    private static final BigDecimal OUT_PRICE = new BigDecimal("0.60");

    private final LlmClient llmClient = mock(LlmClient.class);
    private final DialogSummarizer summarizer = mock(DialogSummarizer.class);
    private final Day11FactExtractor extractor = mock(Day11FactExtractor.class);
    private final TokenEstimator estimator = new TokenEstimator();

    private Day11DialogStore dialogStore;
    private Day11FileMemoryStore memoryStore;
    private Day11DialogService service;
    private String dialogId;

    @BeforeEach
    void setUp() {
        when(extractor.extract(any())).thenReturn(List.of());
        when(summarizer.summarize(any())).thenReturn("Итоговое саммари");
        dialogStore = new Day11DialogStore(tempDir.resolve("dialogs"));
        memoryStore = new Day11FileMemoryStore(tempDir.resolve("memory"));
    }

    private void setup(int window, long limit) {
        service = new Day11DialogService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day11Properties(limit, PRICE, OUT_PRICE, "data/day11-dialogs", "data/day11-memory", window),
                dialogStore,
                new DialogContext(),
                summarizer,
                extractor,
                memoryStore,
                estimator);
        dialogId = service.start().dialogId();
    }

    private LlmReply answer() {
        return new LlmReply("Ответ агента", "stop", 10, 7, 17, new BigDecimal("0.00001"), 100L);
    }

    private Day11MemoryEntry candidate(String key, String value) {
        return new Day11MemoryEntry(key, value, Day11MemoryLayer.SHORT_TERM, "extracted", true, null);
    }

    @Test
    void chatBuildsSystemPromptWithThreeMemoryLayers() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        service.chat(dialogId, "Привет", null);

        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).complete(any(CompletionCommand.class), captor.capture());
        String system = captor.getValue().getFirst().content();
        assertThat(system).contains("Слой памяти: Краткосрочная");
        assertThat(system).contains("Слой памяти: Рабочая");
        assertThat(system).contains("Слой памяти: Долговременная");
        assertThat(system).contains("(пусто)");
        assertThat(captor.getValue().getLast().role()).isEqualTo("user");
        assertThat(captor.getValue().getLast().content()).isEqualTo("Привет");
    }

    @Test
    void chatStoresExtractedCandidatesInShortTermMemory() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());
        when(extractor.extract("Соберём ТЗ")).thenReturn(List.of(candidate("Цель", "собрать ТЗ")));

        Day11ChatResponse response = service.chat(dialogId, "Соберём ТЗ", null);

        assertThat(response.candidates()).extracting(Day11MemoryEntry::key).containsExactly("Цель");
        assertThat(memoryStore.all(Day11MemoryLayer.SHORT_TERM))
                .extracting(Day11MemoryEntry::key).containsExactly("Цель");
        assertThat(memoryStore.all(Day11MemoryLayer.SHORT_TERM).getFirst().isCandidate()).isTrue();
    }

    @Test
    void promoteMovesCandidateToChosenLayerAndClearsShortTerm() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());
        when(extractor.extract("Соберём ТЗ")).thenReturn(List.of(candidate("Цель", "собрать ТЗ")));
        service.chat(dialogId, "Соберём ТЗ", null);

        service.promote(dialogId, "Цель", "working");

        assertThat(memoryStore.all(Day11MemoryLayer.SHORT_TERM)).isEmpty();
        assertThat(memoryStore.find(Day11MemoryLayer.WORKING, "Цель")).isNotNull();
        assertThat(memoryStore.find(Day11MemoryLayer.WORKING, "Цель").isCandidate()).isFalse();
    }

    @Test
    void promoteWithoutTargetLayerUsesRuleSuggestion() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());
        when(extractor.extract("Меня зовут Ася")).thenReturn(List.of(candidate("Имя", "Ася")));
        service.chat(dialogId, "Меня зовут Ася", null);

        service.promote(dialogId, "Имя", null);

        assertThat(memoryStore.find(Day11MemoryLayer.LONG_TERM, "Имя")).isNotNull();
    }

    @Test
    void promoteUnknownCandidateFails() {
        setup(10, 128_000L);

        assertThatThrownBy(() -> service.promote(dialogId, "нет-такого", "working"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    void rememberStoresEntryInExplicitlyChosenLayer() {
        setup(10, 128_000L);

        service.remember(dialogId, "Стек", "Java 21", "long-term");

        Day11MemoryEntry entry = memoryStore.find(Day11MemoryLayer.LONG_TERM, "Стек");
        assertThat(entry).isNotNull();
        assertThat(entry.value()).isEqualTo("Java 21");
        assertThat(entry.source()).isEqualTo("manual");
        assertThat(entry.isCandidate()).isFalse();
    }

    @Test
    void decideRecordsDecisionInLongTermMemory() {
        setup(10, 128_000L);

        service.decide(dialogId, "Берём Java 21");

        List<Day11MemoryEntry> decisions = memoryStore.all(Day11MemoryLayer.LONG_TERM);
        assertThat(decisions).extracting(Day11MemoryEntry::key).containsExactly("решение: 1");
        assertThat(decisions.getFirst().value()).isEqualTo("Берём Java 21");
        assertThat(decisions.getFirst().source()).isEqualTo("decision");
    }

    @Test
    void workingAndLongTermMemoryAreInjectedIntoAnswers() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());
        service.remember(dialogId, "Бюджет", "10 000$", "working");
        service.remember(dialogId, "Имя", "Ася", "long-term");

        service.chat(dialogId, "Что дальше?", null);

        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(1)).complete(any(CompletionCommand.class), captor.capture());
        String system = captor.getValue().getFirst().content();
        assertThat(system).contains("Бюджет: 10 000$");
        assertThat(system).contains("Имя: Ася");
    }

    @Test
    void longTermMemoryPersistsAcrossDialogs() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());
        service.remember(dialogId, "Имя", "Ася", "long-term");

        String secondDialog = service.start().dialogId();
        service.chat(secondDialog, "Как меня зовут?", null);

        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(1)).complete(any(CompletionCommand.class), captor.capture());
        assertThat(captor.getValue().getFirst().content()).contains("Имя: Ася");
    }

    @Test
    void shortTermWindowLimitsDialogueSentToModel() {
        setup(2, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        for (int i = 1; i <= 4; i++) {
            service.chat(dialogId, "Вопрос номер " + i, null);
        }
        Day11ChatResponse last = service.chat(dialogId, "Пятый вопрос", null);

        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(5)).complete(any(CompletionCommand.class), captor.capture());
        assertThat(captor.getAllValues().getLast()).hasSize(4);
        assertThat(captor.getAllValues().getLast().getLast().content()).isEqualTo("Пятый вопрос");
        assertThat(last.messageCount()).isEqualTo(10);
    }

    @Test
    void chatBeyondLimitReturnsExceededWithoutCallingModel() {
        setup(10, 60L);
        when(extractor.extract(any())).thenReturn(List.of());

        Day11ChatResponse response = service.chat(
                dialogId, "Очень длинный вопрос много слов ".repeat(30), null);

        assertThat(response.exceeded()).isTrue();
        assertThat(response.content()).contains("контекстное окно");
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void finishSeedsLongTermMemoryWithDialogSummary() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        service.chat(dialogId, "Разговор для итога", null);
        Day11FinishResponse response = service.finish(dialogId);

        assertThat(response.dialogId()).isEqualTo(dialogId);
        assertThat(response.summary()).isEqualTo("Итоговое саммари");
        assertThat(dialogStore.load(dialogId).isFinished()).isTrue();
        Day11MemoryEntry summary = memoryStore.find(Day11MemoryLayer.LONG_TERM, "итог:" + dialogId);
        assertThat(summary).isNotNull();
        assertThat(summary.value()).isEqualTo("Итоговое саммари");
    }

    @Test
    void metricsReportShowsTokenGrowthAcrossLayers() {
        setup(2, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        for (int i = 1; i <= 4; i++) {
            service.chat(dialogId, "Вопрос номер " + i, null);
        }

        Day11MetricsReport report = service.metrics(dialogId);
        assertThat(report.turns()).hasSize(4);
        assertThat(report.turns().getFirst().turn()).isEqualTo(1);
        assertThat(report.messageCount()).isEqualTo(8);
        assertThat(report.totalTokens()).isPositive();
        assertThat(report.fullTotalTokens()).isGreaterThan(report.totalTokens());
    }

    @Test
    void chatRejectsBlankRequest() {
        setup(10, 128_000L);

        assertThatThrownBy(() -> service.chat(dialogId, " ", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chatOnUnknownDialogThrowsNotFound() {
        setup(10, 128_000L);

        assertThatThrownBy(() -> service.chat("no-such-dialog", "Вопрос?", null))
                .isInstanceOf(DialogNotFoundException.class);
    }

    @Test
    void chatInFinishedDialogIsRejected() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());
        service.chat(dialogId, "Вопрос", null);
        service.finish(dialogId);

        assertThatThrownBy(() -> service.chat(dialogId, "Ещё вопрос", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("завершён");
    }

    @Test
    void dialogsListsAllDialogs() {
        setup(10, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        service.chat(dialogId, "Вопрос", null);
        service.finish(dialogId);
        String second = service.start().dialogId();

        assertThat(service.dialogs()).extracting(Dialog::id).contains(dialogId, second);
        verify(summarizer, atLeastOnce()).summarize(any());
    }
}