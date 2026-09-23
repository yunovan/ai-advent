package com.yunovan.aiadvent.day15;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
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

class Day15TaskServiceTest {

    @TempDir
    Path tempDir;

    private final LlmClient llmClient = mock(LlmClient.class);

    private Day15TaskStore store;
    private Day15TaskService service;

    @BeforeEach
    void setUp() {
        store = new Day15TaskStore(tempDir);
        service = new Day15TaskService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day15Properties(128_000L, new BigDecimal("0.15"), new BigDecimal("0.60"), "data/day15-tasks", 10),
                store,
                new DialogContext(),
                new TokenEstimator());
    }

    private LlmReply answer() {
        return new LlmReply("Продолжаю работу в рамках текущего состояния", "stop", 5, 3, 8, new BigDecimal("0.00001"), 100L);
    }

    private String systemPromptOfLastCall() {
        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, atLeastOnce()).complete(any(CompletionCommand.class), captor.capture());
        return captor.getAllValues().getLast().getFirst().content();
    }

    @Test
    void createStartsInPlanning() {
        Day15Task task = service.create("Переезд на Java 21");

        assertThat(task.stage()).isEqualTo(Day15Stage.PLANNING);
        assertThat(task.step()).isEqualTo(1);
        assertThat(task.paused()).isFalse();
        assertThat(service.list()).extracting(Day15Task::id).contains(task.id());
    }

    @Test
    void createRejectsBlankTitle() {
        assertThatThrownBy(() -> service.create("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transitionWalksThroughControlledLifecycle() {
        Day15Task task = service.create("Переезд на Java 21");

        Day15TaskState approved = service.transition(task.id(), "план утверждён");
        assertThat(approved.stage()).isEqualTo(Day15Stage.PLAN_APPROVED);
        assertThat(approved.allowedTransitions()).containsExactly(Day15Stage.PLANNING, Day15Stage.EXECUTION);

        Day15TaskState executing = service.transition(task.id(), "выполнение");
        assertThat(executing.stage()).isEqualTo(Day15Stage.EXECUTION);

        Day15TaskState validating = service.transition(task.id(), "проверка");
        assertThat(validating.stage()).isEqualTo(Day15Stage.VALIDATION);

        Day15TaskState done = service.transition(task.id(), "готово");
        assertThat(done.stage()).isEqualTo(Day15Stage.DONE);
        assertThat(done.finishedAt()).isNotNull();
        assertThat(done.allowedTransitions()).isEmpty();
        assertThat(store.load(task.id()).isFinished()).isTrue();
    }

    @Test
    void transitionRejectsImplementationBeforeApprovedPlan() {
        Day15Task task = service.create("Задача");

        assertThatThrownBy(() -> service.transition(task.id(), "выполнение"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Недопустимый переход")
                .hasMessageContaining("планирование")
                .hasMessageContaining("выполнение")
                .hasMessageContaining("план утверждён")
                .hasMessageContaining("перепрыгивать")
                .hasMessageContaining("реализация невозможна без утверждённого плана");

        assertThat(store.load(task.id()).stage()).isEqualTo(Day15Stage.PLANNING);
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void transitionRejectsDoneBeforeValidation() {
        Day15Task task = service.create("Задача");
        service.transition(task.id(), "план утверждён");
        service.transition(task.id(), "выполнение");

        assertThatThrownBy(() -> service.transition(task.id(), "готово"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Недопустимый переход")
                .hasMessageContaining("выполнение")
                .hasMessageContaining("готово")
                .hasMessageContaining("проверка")
                .hasMessageContaining("завершение — только после проверки");

        assertThat(store.load(task.id()).stage()).isEqualTo(Day15Stage.EXECUTION);
    }

    @Test
    void advanceWalksCanonicalChainAndFinishes() {
        Day15Task task = service.create("Задача");

        assertThat(service.advance(task.id()).stage()).isEqualTo(Day15Stage.PLAN_APPROVED);
        assertThat(service.advance(task.id()).stage()).isEqualTo(Day15Stage.EXECUTION);
        assertThat(service.advance(task.id()).stage()).isEqualTo(Day15Stage.VALIDATION);
        Day15TaskState done = service.advance(task.id());
        assertThat(done.stage()).isEqualTo(Day15Stage.DONE);
        assertThat(done.finishedAt()).isNotNull();

        assertThatThrownBy(() -> service.advance(task.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("завершена");
    }

    @Test
    void controlledBackwardTransitionToReworkPlanIsAllowed() {
        Day15Task task = service.create("Задача");
        service.transition(task.id(), "план утверждён");

        Day15TaskState backToPlanning = service.transition(task.id(), "планирование");

        assertThat(backToPlanning.stage()).isEqualTo(Day15Stage.PLANNING);
        assertThat(backToPlanning.allowedTransitions()).containsExactly(Day15Stage.PLAN_APPROVED);
    }

    @Test
    void transitionRejectsUnknownTarget() {
        Day15Task task = service.create("Задача");
        assertThatThrownBy(() -> service.transition(task.id(), "космос"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Неизвестное состояние");
    }

    @Test
    void transitionOnDoneThrows() {
        Day15Task task = service.create("Задача");
        service.transition(task.id(), "план утверждён");
        service.transition(task.id(), "выполнение");
        service.transition(task.id(), "проверка");
        service.transition(task.id(), "готово");

        assertThatThrownBy(() -> service.transition(task.id(), "выполнение"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pauseFreezesStateAndTransitionIsBlocked() {
        Day15Task task = service.create("Задача");
        service.pause(task.id());

        assertThatThrownBy(() -> service.transition(task.id(), "план утверждён"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("паузе");
        assertThatThrownBy(() -> service.advance(task.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("паузе");
        assertThat(store.load(task.id()).stage()).isEqualTo(Day15Stage.PLANNING);
    }

    @Test
    void resumeRestoresStateAndTransitionsWork() {
        Day15Task task = service.create("Задача");
        service.pause(task.id());
        assertThat(service.pause(task.id()).paused()).isTrue();

        Day15TaskState resumed = service.resume(task.id());
        assertThat(resumed.paused()).isFalse();
        assertThat(resumed.stage()).isEqualTo(Day15Stage.PLANNING);

        Day15TaskState approved = service.transition(task.id(), "план утверждён");
        assertThat(approved.stage()).isEqualTo(Day15Stage.PLAN_APPROVED);
        assertThat(approved.allowedTransitions()).containsExactly(Day15Stage.PLANNING, Day15Stage.EXECUTION);
    }

    @Test
    void continueOnPausedTaskThrowsWithoutCallingModel() {
        Day15Task task = service.create("Задача");
        service.pause(task.id());

        assertThatThrownBy(() -> service.continueTask(task.id(), "Продолжай", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("паузе");
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void continueAfterResumeSendsRequestAtCurrentStage() {
        Day15Task task = service.create("Задача");
        service.pause(task.id());
        service.resume(task.id());
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        Day15ContinueResponse response = service.continueTask(task.id(), "Продолжай", null);

        assertThat(response.content()).isEqualTo("Продолжаю работу в рамках текущего состояния");
        assertThat(response.state().stage()).isEqualTo(Day15Stage.PLANNING);
        assertThat(store.load(task.id()).history()).hasSize(2);
    }

    @Test
    void continueBuildsControlledTransitionsPrompt() {
        Day15Task task = service.create("Переезд на Java 21");
        service.transition(task.id(), "план утверждён");
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        service.continueTask(task.id(), "Начинай реализацию", null);

        String system = systemPromptOfLastCall();
        assertThat(system).contains("Состояние задачи");
        assertThat(system).contains("Состояние: план утверждён");
        assertThat(system).contains("Разрешённые переходы из 'план утверждён': планирование, выполнение");
        assertThat(system).contains("нельзя перепрыгивать этапы");
        assertThat(system).contains("реализация невозможна без утверждённого плана");
        assertThat(system).contains("завершение возможно только после проверки");
        assertThat(system).contains("Не повторяй объяснения");
    }

    @Test
    void continueStoresHistory() {
        Day15Task task = service.create("Задача");
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        service.continueTask(task.id(), "План?", null);
        service.continueTask(task.id(), "Дальше", null);

        List<Day15TaskMessage> history = store.load(task.id()).history();
        assertThat(history).extracting(Day15TaskMessage::role)
                .containsExactly("user", "assistant", "user", "assistant");
    }

    @Test
    void continueRejectsBlankRequest() {
        Day15Task task = service.create("Задача");
        assertThatThrownBy(() -> service.continueTask(task.id(), "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.continueTask(task.id(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void continueBeyondLimitReturnsExceededWithoutCallingModel() {
        Day15Task task = service.create("Задача");
        service = new Day15TaskService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day15Properties(30L, new BigDecimal("0.15"), new BigDecimal("0.60"), "data/day15-tasks", 10),
                store,
                new DialogContext(),
                new TokenEstimator());

        Day15ContinueResponse response = service.continueTask(
                task.id(), "Очень длинное описание ".repeat(40), null);

        assertThat(response.exceeded()).isTrue();
        assertThat(response.content()).contains("контекстное окно");
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void setStepAndExpectedActionUpdateState() {
        Day15Task task = service.create("Задача");

        Day15TaskState stepped = service.setStep(task.id(), 4);
        assertThat(stepped.step()).isEqualTo(4);

        Day15TaskState expected = service.setExpectedAction(task.id(), "Проверить сборку");
        assertThat(expected.expectedAction()).isEqualTo("Проверить сборку");
    }

    @Test
    void setStepRejectsNonPositive() {
        Day15Task task = service.create("Задача");
        assertThatThrownBy(() -> service.setStep(task.id(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addNoteAppendsToTask() {
        Day15Task task = service.create("Задача");

        service.addNote(task.id(), "План согласован");
        service.addNote(task.id(), "Стек выбран");

        assertThat(store.load(task.id()).notes()).containsExactly("План согласован", "Стек выбран");
    }

    @Test
    void unknownTaskThrowsNotFound() {
        assertThatThrownBy(() -> service.state("no-such"))
                .isInstanceOf(Day15TaskNotFoundException.class);
        assertThatThrownBy(() -> service.advance("no-such"))
                .isInstanceOf(Day15TaskNotFoundException.class);
        assertThatThrownBy(() -> service.transition("no-such", "выполнение"))
                .isInstanceOf(Day15TaskNotFoundException.class);
    }

    @Test
    void continueSendsRequestAsLastMessage() {
        Day15Task task = service.create("Задача");
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        service.continueTask(task.id(), "Первый запрос", null);
        verify(llmClient, times(1)).complete(any(CompletionCommand.class), captor.capture());
        assertThat(captor.getValue().getLast().content()).isEqualTo("Первый запрос");
    }
}