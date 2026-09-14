package com.yunovan.aiadvent.day10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

class Day10DialogServiceTest {

    @TempDir
    Path tempDir;

    private static final BigDecimal PRICE = new BigDecimal("0.15");
    private static final BigDecimal OUT_PRICE = new BigDecimal("0.60");

    private final LlmClient llmClient = mock(LlmClient.class);
    private final DialogSummarizer summarizer = mock(DialogSummarizer.class);
    private final Day10FactExtractor extractor = mock(Day10FactExtractor.class);
    private final TokenEstimator estimator = new TokenEstimator();

    private Day10FileDialogStore store;
    private Day10DialogService service;
    private String dialogId;

    @BeforeEach
    void setUp() {
        when(extractor.extract(any())).thenReturn(List.of());
        when(summarizer.summarize(any())).thenReturn("Итоговое саммари");
        store = new Day10FileDialogStore(tempDir);
    }

    private void setup(Day10Strategy strategy, int window, long limit) {
        service = new Day10DialogService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day10Properties(limit, PRICE, OUT_PRICE, "data/day10-dialogs", window),
                store,
                new DialogContext(),
                summarizer,
                extractor,
                estimator);
        dialogId = service.start(strategy.key(), window).dialogId();
    }

    private LlmReply answer(int number) {
        return new LlmReply("Ответ номер " + number, "stop", 10, 7, 17, new BigDecimal("0.00001"), 100L);
    }

    private static String longQuestion(int number) {
        return "Вопрос номер " + number + " — расскажи подробно, как устроено управление контекстом"
                + " в больших языковых моделях, какие бывают стратегии и чем они отличаются друг от друга"
                + " на практике при сборке требований и ведении длинных диалогов.";
    }

    @Test
    void slidingWindowSendsOnlyLastNMessagesToModelButKeepsFullTranscript() {
        setup(Day10Strategy.SLIDING_WINDOW, 4, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any()))
                .thenAnswer(invocation -> answer((int) (Math.random() * 1000)));

        for (int i = 1; i <= 10; i++) {
            service.chat(dialogId, longQuestion(i), null, null);
        }

        Day10ChatResponse last = service.chat(dialogId, longQuestion(100), null, null);
        assertThat(last.historyTokens()).isLessThan(last.fullHistoryTokens());
        assertThat(last.promptTokens()).isLessThan(last.fullPromptTokens());
        assertThat(last.savedTokens()).isPositive();

        ArgumentCaptor<List<ChatCompletionRequest.Message>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(11)).complete(any(CompletionCommand.class), messagesCaptor.capture());
        List<ChatCompletionRequest.Message> sent = messagesCaptor.getAllValues().getLast();
        assertThat(sent).hasSize(6);
        assertThat(sent.getFirst().role()).isEqualTo("system");
        assertThat(sent.get(1).role()).isEqualTo("user");
        assertThat(sent.getLast().role()).isEqualTo("user");
        assertThat(sent.getLast().content()).isEqualTo(longQuestion(100));

        Day10Dialog stored = store.load(dialogId);
        assertThat(stored.activeMessages()).hasSize(22);
        assertThat(stored.windowSize()).isEqualTo(4);
    }

    @Test
    void windowOverrideIsRespected() {
        setup(Day10Strategy.SLIDING_WINDOW, 8, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer(1));
        for (int i = 1; i <= 3; i++) {
            service.chat(dialogId, "Обычный вопрос " + i, null, null);
        }
        Day10ChatResponse overridden = service.chat(dialogId, "Вопрос с другим окном", null, 2);

        assertThat(overridden.windowSize()).isEqualTo(2);

        ArgumentCaptor<List<ChatCompletionRequest.Message>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(4)).complete(any(CompletionCommand.class), messagesCaptor.capture());
        assertThat(messagesCaptor.getAllValues().getLast()).hasSize(4);
    }

    @Test
    void factsStrategyExtractsFactsAndIncludesThemInSystemPrompt() {
        setup(Day10Strategy.FACTS, 4, 128_000L);
        Day10Fact fact = new Day10Fact("Цель", "собрать ТЗ", true);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer(1));
        when(extractor.extract("Соберём требования")).thenReturn(List.of(fact));

        Day10ChatResponse response = service.chat(dialogId, "Соберём требования", null, null);

        assertThat(response.facts()).extracting(Day10Fact::key).contains("Цель");
        assertThat(response.facts().getFirst().value()).isEqualTo("собрать ТЗ");

        ArgumentCaptor<List<ChatCompletionRequest.Message>> messagesCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(llmClient, times(1)).complete(any(CompletionCommand.class), messagesCaptor.capture());
        assertThat(messagesCaptor.getValue().getFirst().content()).contains("Цель: собрать ТЗ");
        verify(extractor).extract("Соберём требования");
    }

    @Test
    void factsStrategyUpdatesFactValueByKeyOverTime() {
        setup(Day10Strategy.FACTS, 4, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer(1));
        when(extractor.extract("Цена 1000 рублей"))
                .thenReturn(List.of(new Day10Fact("Цена", "1000 рублей", true)));
        when(extractor.extract("Цена выросла до 2000"))
                .thenReturn(List.of(new Day10Fact("Цена", "2000 рублей", true)));

        service.chat(dialogId, "Цена 1000 рублей", null, null);
        service.chat(dialogId, "Цена выросла до 2000", null, null);

        List<Day10Fact> facts = store.load(dialogId).facts();
        assertThat(facts).hasSize(1);
        assertThat(facts.getFirst().key()).isEqualTo("Цена");
        assertThat(facts.getFirst().value()).isEqualTo("2000 рублей");
        assertThat(facts.getFirst().active()).isTrue();
    }

    @Test
    void addFactUpsertsAndTogglesActiveFlag() {
        setup(Day10Strategy.FACTS, 4, 128_000L);

        service.addFact(dialogId, "Стек", "Java 21", true);
        service.addFact(dialogId, "Стек", "Java 21 + Spring Boot", true);
        service.addFact(dialogId, "Дедлайн", "пятница", false);

        List<Day10Fact> facts = service.get(dialogId).facts();
        assertThat(facts).hasSize(2);
        assertThat(facts).filteredOn(fact -> fact.key().equals("Стек"))
                .singleElement().extracting(Day10Fact::value).isEqualTo("Java 21 + Spring Boot");
        assertThat(facts).filteredOn(fact -> fact.key().equals("Дедлайн"))
                .singleElement().extracting(Day10Fact::usable).isEqualTo(false);
    }

    @Test
    void branchingKeepsFullHistoryInModel() {
        setup(Day10Strategy.BRANCHING, 4, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer(1));

        Day10ChatResponse response = service.chat(dialogId, "Первый вопрос", null, null);

        assertThat(response.historyTokens()).isEqualTo(response.fullHistoryTokens());
        assertThat(response.savedTokens()).isZero();
        assertThat(response.messageCount()).isEqualTo(2);
    }

    @Test
    void branchingForkAndSwitchAreIndependent() throws Exception {
        setup(Day10Strategy.BRANCHING, 4, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer(2));

        service.chat(dialogId, "Первый вопрос", null, null);
        service.chat(dialogId, "Второй вопрос", null, null);

        service.checkpoint(dialogId);
        assertThat(store.load(dialogId).checkpointMessageIndex()).isEqualTo(4);
        assertThat(service.createBranch(dialogId).activeBranchId()).isEqualTo("b2");
        assertThat(service.get(dialogId).messageCount()).isEqualTo(4);

        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer(3));
        service.chat(dialogId, "Продолжение во второй ветке", null, null);
        assertThat(service.get(dialogId).messageCount()).isEqualTo(6);

        service.switchBranch(dialogId, "main");
        assertThat(service.get(dialogId).activeBranchId()).isEqualTo("main");
        assertThat(service.get(dialogId).messageCount()).isEqualTo(4);

        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer(4));
        service.chat(dialogId, "Продолжение в основной ветке", null, null);

        Day10Dialog stored = store.load(dialogId);
        Day10Branch main = stored.branches().stream().filter(b -> b.id().equals("main")).findFirst().orElseThrow();
        Day10Branch fork = stored.branches().stream().filter(b -> b.id().equals("b2")).findFirst().orElseThrow();
        assertThat(main.messages()).hasSize(6);
        assertThat(main.messages().getLast().content()).isEqualTo("Ответ номер 4");
        assertThat(fork.messages()).hasSize(6);
        assertThat(fork.messages().getLast().content()).isEqualTo("Ответ номер 3");
    }

    @Test
    void checkpointRequiresBranchingStrategy() {
        setup(Day10Strategy.SLIDING_WINDOW, 4, 128_000L);

        assertThatThrownBy(() -> service.checkpoint(dialogId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Branching");
    }

    @Test
    void createBranchRequiresCheckpointFirst() {
        setup(Day10Strategy.BRANCHING, 4, 128_000L);

        assertThatThrownBy(() -> service.createBranch(dialogId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("checkpoint");
    }

    @Test
    void switchBranchWithUnknownBranchThrows() {
        setup(Day10Strategy.BRANCHING, 4, 128_000L);

        assertThatThrownBy(() -> service.switchBranch(dialogId, "nope"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void chatBeyondLimitReturnsExceededWithoutCallingModel() {
        setup(Day10Strategy.SLIDING_WINDOW, 10, 60L);

        Day10ChatResponse response = service.chat(dialogId, longQuestion(1).repeat(3).repeat(3), null, null);

        assertThat(response.exceeded()).isTrue();
        assertThat(response.content()).contains("контекстное окно");
        assertThat(response.messageCount()).isZero();
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void finishSummarizesAndMarksDialogFinished() {
        setup(Day10Strategy.SLIDING_WINDOW, 8, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer(1));

        service.chat(dialogId, "Разговор для саммари", null, null);
        Day10FinishResponse response = service.finish(dialogId);

        assertThat(response.dialogId()).isEqualTo(dialogId);
        assertThat(response.summary()).isEqualTo("Итоговое саммари");
        assertThat(store.load(dialogId).isFinished()).isTrue();
        assertThat(service.dialogs()).anyMatch(summary -> summary.dialogId().equals(dialogId)
                && summary.finishedAt() != null);
    }

    @Test
    void metricsReportShowsStrategyTotals() {
        setup(Day10Strategy.SLIDING_WINDOW, 4, 128_000L);
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer(1));

        for (int i = 1; i <= 6; i++) {
            service.chat(dialogId, longQuestion(i), null, null);
        }

        Day10GrowthReport report = service.metrics(dialogId);
        assertThat(report.strategy()).isEqualTo(Day10Strategy.SLIDING_WINDOW);
        assertThat(report.turns()).hasSize(6);
        assertThat(report.turns().getFirst().turn()).isEqualTo(1);
        assertThat(report.totalTokens()).isLessThan(report.fullTotalTokens());
        assertThat(report.totalCostUsd()).isLessThan(report.fullTotalCostUsd());
        assertThat(report.windowSize()).isEqualTo(4);
        assertThat(report.factsCount()).isZero();
        assertThat(report.branchCount()).isEqualTo(1);
    }

    @Test
    void startUsesSlidingWindowWhenNoStrategyGiven() {
        setUp();
        service = new Day10DialogService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day10Properties(128_000L, PRICE, OUT_PRICE, "data/day10-dialogs", 8),
                store,
                new DialogContext(),
                summarizer,
                extractor,
                estimator);

        Day10StartResponse response = service.start(null, null);

        assertThat(response.strategy()).isEqualTo(Day10Strategy.SLIDING_WINDOW);
        assertThat(response.windowSize()).isEqualTo(8);
    }

    @Test
    void chatRejectsBlankRequest() {
        setup(Day10Strategy.SLIDING_WINDOW, 8, 128_000L);

        assertThatThrownBy(() -> service.chat(dialogId, "   ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chatOnUnknownDialogThrowsNotFound() {
        setup(Day10Strategy.SLIDING_WINDOW, 8, 128_000L);

        assertThatThrownBy(() -> service.chat("no-such-dialog", "Вопрос", null, null))
                .isInstanceOf(DialogNotFoundException.class);
    }
}