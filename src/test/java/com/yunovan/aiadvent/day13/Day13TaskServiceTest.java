package com.yunovan.aiadvent.day13;

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

class Day13TaskServiceTest {

    @TempDir
    Path tempDir;

    private final LlmClient llmClient = mock(LlmClient.class);

    private Day13TaskStore store;
    private Day13TaskService service;

    @BeforeEach
    void setUp() {
        store = new Day13TaskStore(tempDir);
        service = new Day13TaskService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day13Properties(128_000L, new BigDecimal("0.15"), new BigDecimal("0.60"), "data/day13-tasks", 10),
                store,
                new DialogContext(),
                new TokenEstimator());
    }

    private LlmReply answer() {
        return new LlmReply("Продолжаю выполнение шага", "stop", 5, 3, 8, new BigDecimal("0.00001"), 100L);
    }

    private String systemPromptOfLastCall() {
        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, atLeastOnce()).complete(any(CompletionCommand.class), captor.capture());
        return captor.getAllValues().getLast().getFirst().content();
    }

    @Test
    void createStartsInPlanningWithStepOne() {
        Day13Task task = service.create("Переезд на Java 21");

        assertThat(task.stage()).isEqualTo(Day13Stage.PLANNING);
        assertThat(task.step()).isEqualTo(1);
        assertThat(task.paused()).isFalse();
        assertThat(task.isFinished()).isFalse();
        assertThat(service.list()).extracting(Day13Task::id).contains(task.id());
    }

    @Test
    void createRejectsBlankTitle() {
        assertThatThrownBy(() -> service.create("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void advanceWalksThroughEveryStage() {
        Day13Task task = service.create("Задача");

        Day13TaskState state1 = service.advance(task.id());
        assertThat(state1.stage()).isEqualTo(Day13Stage.EXECUTION);
        assertThat(state1.step()).isEqualTo(1);

        Day13TaskState state2 = service.advance(task.id());
        assertThat(state2.stage()).isEqualTo(Day13Stage.VALIDATION);

        Day13TaskState state3 = service.advance(task.id());
        assertThat(state3.stage()).isEqualTo(Day13Stage.DONE);
        assertThat(state3.finishedAt()).isNotNull();
        assertThat(store.load(task.id()).isFinished()).isTrue();
    }

    @Test
    void advanceOnDoneThrows() {
        Day13Task task = service.create("Задача");
        service.advance(task.id());
        service.advance(task.id());
        service.advance(task.id());

        assertThatThrownBy(() -> service.advance(task.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("завершена");
    }

    @Test
    void pauseAndResumeWorksAtAnyStage() {
        Day13Task task = service.create("Задача");

        Day13TaskState paused = service.pause(task.id());
        assertThat(paused.paused()).isTrue();
        Day13TaskState resumed = service.resume(task.id());
        assertThat(resumed.paused()).isFalse();

        service.advance(task.id());
        assertThat(service.pause(task.id()).paused()).isTrue();
        service.resume(task.id());

        service.advance(task.id());
        assertThat(service.pause(task.id()).paused()).isTrue();
        service.resume(task.id());
    }

    @Test
    void pauseOnDoneThrows() {
        Day13Task task = service.create("Задача");
        service.advance(task.id());
        service.advance(task.id());
        service.advance(task.id());

        assertThatThrownBy(() -> service.pause(task.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void continueOnPausedTaskThrows() {
        Day13Task task = service.create("Задача");
        service.pause(task.id());

        assertThatThrownBy(() -> service.continueTask(task.id(), "Продолжай", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("паузе");
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void continueAfterResumeSendsRequestToLlm() {
        Day13Task task = service.create("Задача");
        service.pause(task.id());
        service.resume(task.id());
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        Day13ContinueResponse response = service.continueTask(task.id(), "Продолжай", null);

        assertThat(response.content()).isEqualTo("Продолжаю выполнение шага");
        assertThat(response.model()).isEqualTo("gpt-4o-mini");
        assertThat(response.state().stage()).isEqualTo(Day13Stage.PLANNING);
        assertThat(store.load(task.id()).history()).hasSize(2);
    }

    @Test
    void continueBuildsStateBlockWithoutRepeatingExplanations() {
        Day13Task task = service.create("Переезд на Java 21");
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        service.continueTask(task.id(), "Составь план", null);
        service.advance(task.id());
        service.continueTask(task.id(), "Выполняй шаг 1", null);

        String system = systemPromptOfLastCall();
        assertThat(system).contains("Состояние задачи");
        assertThat(system).contains("Этап: выполнение");
        assertThat(system).contains("Текущий шаг: 1");
        assertThat(system).contains("Ожидаемое действие");
        assertThat(system).contains("Продолжай работу с учётом текущего этапа");
        assertThat(system).contains("Не повторяй объяснения, уже данные ранее");
    }

    @Test
    void continueStoresTaskHistory() {
        Day13Task task = service.create("Задача");
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        service.continueTask(task.id(), "План?", null);
        service.continueTask(task.id(), "Дальше", null);

        List<Day13TaskMessage> history = store.load(task.id()).history();
        assertThat(history).extracting(Day13TaskMessage::role)
                .containsExactly("user", "assistant", "user", "assistant");
        assertThat(history.getLast().content()).isEqualTo("Продолжаю выполнение шага");
    }

    @Test
    void continueBeyondLimitReturnsExceededWithoutCallingModel() {
        Day13Task task = service.create("Задача");
        service = new Day13TaskService(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                new Day13Properties(30L, new BigDecimal("0.15"), new BigDecimal("0.60"), "data/day13-tasks", 10),
                store,
                new DialogContext(),
                new TokenEstimator());

        Day13ContinueResponse response = service.continueTask(
                task.id(), "Очень длинное описание ".repeat(40), null);

        assertThat(response.exceeded()).isTrue();
        assertThat(response.content()).contains("контекстное окно");
        verify(llmClient, never()).complete(any(CompletionCommand.class), any());
    }

    @Test
    void continueRejectsBlankRequest() {
        Day13Task task = service.create("Задача");
        assertThatThrownBy(() -> service.continueTask(task.id(), "  ", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.continueTask(task.id(), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void setStepAndExpectedActionUpdateState() {
        Day13Task task = service.create("Задача");

        Day13TaskState stepped = service.setStep(task.id(), 4);
        assertThat(stepped.step()).isEqualTo(4);

        Day13TaskState expected = service.setExpectedAction(task.id(), "Проверить сборку");
        assertThat(expected.expectedAction()).isEqualTo("Проверить сборку");
    }

    @Test
    void setStepRejectsNonPositive() {
        Day13Task task = service.create("Задача");
        assertThatThrownBy(() -> service.setStep(task.id(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addNoteAppendsToTask() {
        Day13Task task = service.create("Задача");

        service.addNote(task.id(), "План согласован");
        service.addNote(task.id(), "Стек выбран");

        assertThat(store.load(task.id()).notes()).containsExactly("План согласован", "Стек выбран");
    }

    @Test
    void unknownTaskThrowsNotFound() {
        assertThatThrownBy(() -> service.state("no-such"))
                .isInstanceOf(Day13TaskNotFoundException.class);
        assertThatThrownBy(() -> service.continueTask("no-such", "Продолжай", null))
                .isInstanceOf(Day13TaskNotFoundException.class);
        assertThatThrownBy(() -> service.pause("no-such"))
                .isInstanceOf(Day13TaskNotFoundException.class);
    }

    @Test
    void continueSendsHistoryMessages() {
        Day13Task task = service.create("Задача");
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(answer());

        ArgumentCaptor<List<ChatCompletionRequest.Message>> captor = ArgumentCaptor.forClass(List.class);
        service.continueTask(task.id(), "Первый запрос", null);
        verify(llmClient, times(1)).complete(any(CompletionCommand.class), captor.capture());
        assertThat(captor.getValue().getLast().content()).isEqualTo("Первый запрос");
    }
}