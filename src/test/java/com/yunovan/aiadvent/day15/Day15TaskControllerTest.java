package com.yunovan.aiadvent.day15;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.llm.LlmException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day15TaskController.class, ApiExceptionHandler.class})
class Day15TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day15TaskService service;

    private static Day15Task task(String id, Day15Stage stage) {
        Instant now = Instant.now();
        return new Day15Task(
                id, "Переезд на Java 21", stage, 1, "Выполнить шаг", false,
                List.of(), List.of(), now, now, null);
    }

    private static Day15TaskState state(String id, Day15Stage stage) {
        return new Day15TaskState(
                id, "Переезд на Java 21", stage, Day15StateMachine.allowedTargets(stage),
                1, "Выполнить шаг", false,
                List.of(), Instant.now(), Instant.now(), null);
    }

    @Test
    void postCreateCreatesTask() throws Exception {
        when(service.create("Переезд на Java 21")).thenReturn(task("t1", Day15Stage.PLANNING));

        mockMvc.perform(post("/api/day15/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Переезд на Java 21\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("t1"))
                .andExpect(jsonPath("$.stage").value("PLANNING"))
                .andExpect(jsonPath("$.step").value(1));
    }

    @Test
    void getTasksListsAll() throws Exception {
        when(service.list()).thenReturn(List.of(task("t1", Day15Stage.PLAN_APPROVED), task("t2", Day15Stage.DONE)));

        mockMvc.perform(get("/api/day15/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].stage").value("PLAN_APPROVED"))
                .andExpect(jsonPath("$[1].stage").value("DONE"));
    }

    @Test
    void getTaskReturnsIt() throws Exception {
        when(service.get("t1")).thenReturn(task("t1", Day15Stage.EXECUTION));

        mockMvc.perform(get("/api/day15/tasks/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("EXECUTION"));
    }

    @Test
    void getStateIncludesAllowedTransitions() throws Exception {
        when(service.state("t1")).thenReturn(state("t1", Day15Stage.PLAN_APPROVED));

        mockMvc.perform(get("/api/day15/tasks/t1/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("PLAN_APPROVED"))
                .andExpect(jsonPath("$.allowedTransitions[0]").value("PLANNING"))
                .andExpect(jsonPath("$.allowedTransitions[1]").value("EXECUTION"));
    }

    @Test
    void postTransitionToAllowedTarget() throws Exception {
        when(service.transition("t1", "выполнение")).thenReturn(state("t1", Day15Stage.EXECUTION));

        mockMvc.perform(post("/api/day15/tasks/t1/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"выполнение\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("EXECUTION"));
    }

    @Test
    void postTransitionMapsIllegalJumpToBadRequest() throws Exception {
        when(service.transition("t1", "выполнение"))
                .thenThrow(new IllegalArgumentException(
                        "Недопустимый переход: 'планирование' → 'выполнение'... перепрыгивать..."
                                + " реализация невозможна без утверждённого плана"));

        mockMvc.perform(post("/api/day15/tasks/t1/transition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"target\":\"выполнение\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Недопустимый переход: 'планирование' → 'выполнение'... перепрыгивать..."
                                + " реализация невозможна без утверждённого плана"));
    }

    @Test
    void postAdvanceMovesToNextStage() throws Exception {
        when(service.advance("t1")).thenReturn(state("t1", Day15Stage.PLAN_APPROVED));

        mockMvc.perform(post("/api/day15/tasks/t1/advance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("PLAN_APPROVED"));
    }

    @Test
    void postPauseSetsPaused() throws Exception {
        when(service.pause("t1")).thenReturn(new Day15TaskState(
                "t1", "Переезд на Java 21", Day15Stage.EXECUTION,
                Day15StateMachine.allowedTargets(Day15Stage.EXECUTION), 2, "Выполнить шаг",
                true, List.of(), Instant.now(), Instant.now(), null));

        mockMvc.perform(post("/api/day15/tasks/t1/pause"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(true));
    }

    @Test
    void postResumeClearsPaused() throws Exception {
        when(service.resume("t1")).thenReturn(state("t1", Day15Stage.EXECUTION));

        mockMvc.perform(post("/api/day15/tasks/t1/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paused").value(false));
    }

    @Test
    void postExpectedActionUpdatesIt() throws Exception {
        when(service.setExpectedAction("t1", "Проверить сборку")).thenReturn(new Day15TaskState(
                "t1", "Переезд на Java 21", Day15Stage.VALIDATION,
                Day15StateMachine.allowedTargets(Day15Stage.VALIDATION), 1, "Проверить сборку",
                false, List.of(), Instant.now(), Instant.now(), null));

        mockMvc.perform(post("/api/day15/tasks/t1/expected-action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedAction\":\"Проверить сборку\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expectedAction").value("Проверить сборку"));
    }

    @Test
    void postContinueReturnsAgentReplyAndState() throws Exception {
        when(service.continueTask("t1", "Выполняй шаг 1", null)).thenReturn(new Day15ContinueResponse(
                "t1", "Выполняй шаг 1", "Продолжаю работу", "gpt-4o-mini", 100L,
                20, 5, 8, 4, 33, 128000L, false, state("t1", Day15Stage.EXECUTION)));

        mockMvc.perform(post("/api/day15/tasks/t1/continue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Выполняй шаг 1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Продолжаю работу"))
                .andExpect(jsonPath("$.state.stage").value("EXECUTION"))
                .andExpect(jsonPath("$.exceeded").value(false));
    }

    @Test
    void postContinueMapsLlmFailureToBadGateway() throws Exception {
        when(service.continueTask(any(), any(), any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day15/tasks/t1/continue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }

    @Test
    void postContinueMapsUnknownTaskToNotFound() throws Exception {
        when(service.continueTask(any(), any(), any()))
                .thenThrow(new Day15TaskNotFoundException("t1"));

        mockMvc.perform(post("/api/day15/tasks/nope/continue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Задача 't1' не найдена"));
    }
}