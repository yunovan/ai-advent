package com.yunovan.aiadvent.day35;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day35Controller.class, ApiExceptionHandler.class})
class Day35ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day35MathTutorService service;

    @MockitoBean
    private Day35HealthService healthService;

    private static Day35Problem problem() {
        return new Day35Problem("alg-1", "Решите 2x+5=15", "Перенесите 5", "5-7");
    }

    private static Day35Solution solution() {
        return new Day35Solution("alg-1", "5", "Ответ верный.", "Шаг 1...", "correct",
                "qwen2.5:3b", 42L, 10, 8, 1.9);
    }

    private static Day35SessionResponse response(boolean finished, boolean correct) {
        return new Day35SessionResponse("sid-1", "Задача 1 из 2", problem(), List.of(problem()),
                solution(), new Day35ErrorAnalysis("Ошибок нет", "", "Так держать"),
                new Day35Progress(2, 1, 1, List.of("algebra")), finished, correct,
                "Готово", "qwen2.5:3b", 42L, 10, 8, 1.9);
    }

    @Test
    void healthReportsModelAvailability() throws Exception {
        when(healthService.health()).thenReturn(
                new Day35HealthService.Day35Health(true, "ai-advent-math-tutor", "0.1.0", "qwen2.5:3b", ""));

        mockMvc.perform(get("/api/day35/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.name").value("ai-advent-math-tutor"))
                .andExpect(jsonPath("$.model").value("qwen2.5:3b"));
    }

    @Test
    void lessonsAreReturned() throws Exception {
        when(service.lessons()).thenReturn(List.of(new Day35Lesson("algebra", "Алгебра", "5-7", "Уравнения")));

        mockMvc.perform(get("/api/day35/lessons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("algebra"))
                .andExpect(jsonPath("$[0].topic").value("Алгебра"));
    }

    @Test
    void startReturnsFirstProblem() throws Exception {
        when(service.start(any())).thenReturn(response(false, false));

        mockMvc.perform(post("/api/day35/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"algebra\",\"count\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sid-1"))
                .andExpect(jsonPath("$.problem.id").value("alg-1"))
                .andExpect(jsonPath("$.problems.length()").value(1))
                .andExpect(jsonPath("$.progress.total").value(2));
    }

    @Test
    void answerReturnsReviewAndProgress() throws Exception {
        when(service.answer(eq("sid-1"), any())).thenReturn(response(true, true));

        mockMvc.perform(post("/api/day35/sessions/sid-1/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userAnswer\":\"5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correct").value(true))
                .andExpect(jsonPath("$.finished").value(true))
                .andExpect(jsonPath("$.solution.check").value("correct"))
                .andExpect(jsonPath("$.progress.correct").value(1))
                .andExpect(jsonPath("$.model").value("qwen2.5:3b"));
    }

    @Test
    void unknownSessionReturnsNotFound() throws Exception {
        when(service.answer(eq("missing"), any()))
                .thenThrow(new Day35Exception("Сессия не найдена: missing"));

        mockMvc.perform(post("/api/day35/sessions/missing/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userAnswer\":\"5\"}"))
                .andExpect(status().isNotFound());
    }
}
