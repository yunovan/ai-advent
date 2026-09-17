package com.yunovan.aiadvent.day14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.agent.dialog.DialogContext;
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

class Day14InvariantServiceTest {

    @TempDir
    Path tempDir;

    private final LlmClient llmClient = mock(LlmClient.class);

    private Day14InvariantStore store;
    private Day14InvariantService service;

    @BeforeEach
    void setUp() {
        store = new Day14InvariantStore(tempDir);
        service = new Day14InvariantService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day14Properties(128_000L, new BigDecimal("0.15"), new BigDecimal("0.60"), "data/day14-invariants", 10),
                store,
                new DialogContext(),
                new TokenEstimator());
    }

    private LlmReply answer() {
        return new LlmReply("Не могу предложить MySQL: нарушен инвариант стека (только PostgreSQL)",
                "stop", 30, 6, 36, new BigDecimal("0.00001"), 100L);
    }

    private String systemPromptOfLastCall() {
        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient).complete(any(CompletionCommand.class), captor.capture());
        return captor.getValue().getFirst().content();
    }

    @Test
    void createStoresInvariantWithCategory() {
        Day14Invariant invariant = service.create("стек", "База данных", "только PostgreSQL");

        assertThat(invariant.category()).isEqualTo(Day14Category.STACK);
        assertThat(invariant.active()).isTrue();
        assertThat(service.list()).extracting(Day14Invariant::title).contains("База данных");
    }

    @Test
    void createRejectsBlankTitleOrDescription() {
        assertThatThrownBy(() -> service.create("стек", "  ", "описание"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("стек", "Название", "  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create("стек", null, "описание"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createRejectsUnknownCategory() {
        assertThatThrownBy(() -> service.create("космос", "База", "PostgreSQL"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("категори");
    }

    @Test
    void activeOnlyReturnsEnabledInvariants() {
        service.create("стек", "База данных", "только PostgreSQL");
        Day14Invariant disabled = service.create("бизнес", "Скидки", "максимум 10%");
        service.deactivate(disabled.id());

        assertThat(service.active()).extracting(Day14Invariant::title).containsExactly("База данных");
        assertThat(service.list()).hasSize(2);
    }

    @Test
    void deactivateMarksInvariantInactive() {
        Day14Invariant invariant = service.create("архитектура", "Микросервисы", "без монолита");

        Day14Invariant updated = service.deactivate(invariant.id());

        assertThat(updated.active()).isFalse();
        assertThat(service.get(invariant.id()).active()).isFalse();
    }

    @Test
    void missingInvariantThrows() {
        assertThatThrownBy(() -> service.get("no-such"))
                .isInstanceOf(Day14InvariantNotFoundException.class);
        assertThatThrownBy(() -> service.deactivate("no-such"))
                .isInstanceOf(Day14InvariantNotFoundException.class);
        assertThatThrownBy(() -> service.delete("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adviseReturnsAgentReplyAndUsedInvariants() {
        service.create("стек", "База данных", "только PostgreSQL");
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        Day14AdviseResponse response = service.advise("Заменим базу на MySQL?", null);

        assertThat(response.content()).contains("Не могу предложить MySQL");
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
        assertThat(response.exceeded()).isFalse();
        assertThat(response.invariants()).extracting(Day14Invariant::title).contains("База данных");
    }

    @Test
    void adviseInsertsActiveInvariantsAndRefusalRulesIntoSystemPrompt() {
        service.create("стек", "База данных", "только PostgreSQL");
        service.create("бизнес", "Скидки", "максимум 10%");
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        service.advise("Нужен ли индекс по email?", null);

        String system = systemPromptOfLastCall();
        assertThat(system).contains("Инварианты проекта");
        assertThat(system).contains("обязательны к соблюдению");
        assertThat(system).contains("[стек] База данных: только PostgreSQL");
        assertThat(system).contains("[бизнес] Скидки: максимум 10%");
        assertThat(system).contains("Явно учитывай каждый инвариант");
        assertThat(system).contains("ОТКАЖИСЬ");
        assertThat(system).contains("какой инвариант нарушен");
    }

    @Test
    void adviseExcludesInactiveInvariantsFromPrompt() {
        Day14Invariant irrelevant = service.create("бизнес", "Скидки", "максимум 10%");
        service.create("стек", "База данных", "только PostgreSQL");
        service.deactivate(irrelevant.id());
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        service.advise("Вопрос", null);

        String system = systemPromptOfLastCall();
        assertThat(system).contains("База данных");
        assertThat(system).doesNotContain("Скидки");
    }

    @Test
    void adviseSendsRequestAsLastUserMessage() {
        service.create("стек", "База данных", "только PostgreSQL");
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        service.advise("Заменим на MongoDB?", null);

        verify(llmClient, times(1)).complete(any(CompletionCommand.class), captor.capture());
        List<ChatCompletionRequest.Message> messages = captor.getValue();
        assertThat(messages.getLast().role()).isEqualTo("user");
        assertThat(messages.getLast().content()).isEqualTo("Заменим на MongoDB?");
    }

    @Test
    void adviseRejectsBlankRequest() {
        assertThatThrownBy(() -> service.advise("  ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.advise(null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void adviseBeyondLimitReturnsExceededWithoutCallingModel() {
        service = new Day14InvariantService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day14Properties(30L, new BigDecimal("0.15"), new BigDecimal("0.60"), "data/day14-invariants", 10),
                store,
                new DialogContext(),
                new TokenEstimator());

        Day14AdviseResponse response = service.advise("Очень длинный запрос ".repeat(40), null);

        assertThat(response.exceeded()).isTrue();
        assertThat(response.content()).contains("контекстное окно");
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }
}