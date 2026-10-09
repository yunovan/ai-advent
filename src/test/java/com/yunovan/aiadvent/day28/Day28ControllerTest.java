package com.yunovan.aiadvent.day28;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day26.Day26LlmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day28Controller.class, ApiExceptionHandler.class})
class Day28ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day28RagService service;

    private static Day28HealthResponse health() {
        return new Day28HealthResponse(9, 131000, 44, "fixed", 10, 5, 0.5, true, 400, 2,
                "локальный: n-gram эмбеддинги", "http://localhost:11434", "qwen2.5:3b",
                true, "0.35.1", true, true, "openai/gpt-4o-mini", false, "");
    }

    private static Day21Chunk chunk() {
        return new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                "Эмбеддинги", "Признаковые алгоритмы", "articles/embeddings.md#fixed#0001",
                0, 100, "Эмбеддинги — это векторы.");
    }

    private static Day28AnswerResponse answer() {
        return new Day28AnswerResponse("Что такое эмбеддинги?", "local", "qwen2.5:3b",
                "эмбеддинги вектор", true, 2, 1,
                List.of(new Day21SearchHit(chunk(), 1.0, "эмбеддинги и векторы")),
                List.of("embeddings.md"), "Ответ локальной модели", false, 87.5,
                3_100L, 45, 25, 5.3, "");
    }

    private static Day28CompareResponse compare() {
        return new Day28CompareResponse("Что такое эмбеддинги?", answer(),
                new Day28AnswerResponse("Что такое эмбеддинги?", "cloud",
                        "openai/gpt-4o-mini", "эмбеддинги вектор", true, 2, 1,
                        List.of(new Day21SearchHit(chunk(), 1.0, "эмбеддинги и векторы")),
                        List.of("embeddings.md"), "Облачный ответ", false, 60.0,
                        1_000L, 45, 25, 25.0, ""),
                true, List.of("embeddings.md"),
                "Один и тот же локальный контекст из 1 источников (embeddings.md).");
    }

    private static Day28EngineStats localStats() {
        return new Day28EngineStats(true, "", 20, 0, 6.7, 0.0, 3_100.0, 120.0, 5.3);
    }

    private static Day28EngineStats cloudStats() {
        return new Day28EngineStats(true, "", 20, 0, 6.7, 1.4, 1_000.0, 50.0, 25.0);
    }

    private static Day28EvalResponse evaluate() {
        return new Day28EvalResponse(10, 2, 1, 10.0, localStats(), cloudStats(),
                "Качество (покрытие ключевых слов): локальная модель 6.7% против облачной 6.7%.",
                "Средняя задержка: локальная 3100 мс против облачной 1000 мс. Быстрее — облачная модель.",
                "Отклонение покрытия между повторами: локальная ±0.0 п.п. — ответы стабильны.",
                List.of(new Day28EvalItem("q01", "Что такое эмбеддинги?",
                        List.of("эмбеддинг"), List.of("embeddings.md"),
                        List.of("embeddings.md"), true,
                        List.of(100.0, 100.0), List.of(3_100L, 3_100L),
                        List.of(100.0, 100.0), List.of(1_000L, 1_000L))));
    }

    @Test
    void healthReturnsLocalRetrievalStateWithoutUsingCloud() throws Exception {
        when(service.health()).thenReturn(health());

        mockMvc.perform(get("/api/day28/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(9))
                .andExpect(jsonPath("$.strategy").value("fixed"))
                .andExpect(jsonPath("$.topKBefore").value(10))
                .andExpect(jsonPath("$.topKAfter").value(5))
                .andExpect(jsonPath("$.threshold").value(0.5))
                .andExpect(jsonPath("$.retrieval").value("локальный: n-gram эмбеддинги"))
                .andExpect(jsonPath("$.localAvailable").value(true))
                .andExpect(jsonPath("$.localVersion").value("0.35.1"))
                .andExpect(jsonPath("$.modelInstalled").value(true))
                .andExpect(jsonPath("$.cloudConfigured").value(true))
                .andExpect(jsonPath("$.usesCloud").value(false))
                .andExpect(jsonPath("$.localError").value(""));
    }

    @Test
    void questionsReturnsControlQuestions() throws Exception {
        when(service.questions()).thenReturn(List.of(
                new com.yunovan.aiadvent.day22.Day22ControlQuestion("q01", "Вопрос?",
                        List.of("ключевое"), List.of("embeddings.md"))));

        mockMvc.perform(get("/api/day28/questions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("q01"))
                .andExpect(jsonPath("$[0].expectedSources[0]").value("embeddings.md"));
    }

    @Test
    void askReturnsLocalAnswerWithMetrics() throws Exception {
        when(service.ask("Что такое эмбеддинги?")).thenReturn(answer());

        mockMvc.perform(post("/api/day28/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Что такое эмбеддинги?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.engine").value("local"))
                .andExpect(jsonPath("$.model").value("qwen2.5:3b"))
                .andExpect(jsonPath("$.rewritten").value(true))
                .andExpect(jsonPath("$.candidatesBefore").value(2))
                .andExpect(jsonPath("$.filteredOut").value(1))
                .andExpect(jsonPath("$.retrievedHits.length()").value(1))
                .andExpect(jsonPath("$.sources[0]").value("embeddings.md"))
                .andExpect(jsonPath("$.answer").value("Ответ локальной модели"))
                .andExpect(jsonPath("$.fallback").value(false))
                .andExpect(jsonPath("$.groundingPercent").value(87.5))
                .andExpect(jsonPath("$.promptTokens").value(45))
                .andExpect(jsonPath("$.outputTokens").value(25))
                .andExpect(jsonPath("$.tokensPerSecond").value(5.3))
                .andExpect(jsonPath("$.unavailableReason").value(""));
    }

    @Test
    void askWithBlankQuestionReturnsBadRequest() throws Exception {
        when(service.ask(anyString()))
                .thenThrow(new IllegalArgumentException("Вопрос не может быть пустым"));

        mockMvc.perform(post("/api/day28/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Вопрос не может быть пустым"));
    }

    @Test
    void askWithMissingQuestionReturnsBadRequest() throws Exception {
        when(service.ask(null))
                .thenThrow(new IllegalArgumentException("Вопрос не может быть пустым"));

        mockMvc.perform(post("/api/day28/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void askWhenLocalLlmProbeFailsStillReturnsAnswer() throws Exception {
        when(service.ask("Вопрос")).thenThrow(
                new Day26LlmException("Локальный LLM недоступен на http://localhost:11434"));

        mockMvc.perform(post("/api/day28/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Вопрос\"}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void compareReturnsBothEnginesAndVerdict() throws Exception {
        when(service.compare("Что такое эмбеддинги?")).thenReturn(compare());

        mockMvc.perform(post("/api/day28/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Что такое эмбеддинги?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.local.engine").value("local"))
                .andExpect(jsonPath("$.cloud.engine").value("cloud"))
                .andExpect(jsonPath("$.cloud.answer").value("Облачный ответ"))
                .andExpect(jsonPath("$.cloudConfigured").value(true))
                .andExpect(jsonPath("$.sources[0]").value("embeddings.md"))
                .andExpect(jsonPath("$.verdict").value(
                        "Один и тот же локальный контекст из 1 источников (embeddings.md)."));
    }

    @Test
    void compareWithBlankQuestionReturnsBadRequest() throws Exception {
        when(service.compare(anyString()))
                .thenThrow(new IllegalArgumentException("Вопрос не может быть пустым"));

        mockMvc.perform(post("/api/day28/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Вопрос не может быть пустым"));
    }

    @Test
    void evaluateReturnsAggregatedMetricsAndItems() throws Exception {
        when(service.evaluate()).thenReturn(evaluate());

        mockMvc.perform(post("/api/day28/evaluate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10))
                .andExpect(jsonPath("$.runs").value(2))
                .andExpect(jsonPath("$.retrievalHits").value(1))
                .andExpect(jsonPath("$.retrievalRecallPercent").value(10.0))
                .andExpect(jsonPath("$.local.answered").value(20))
                .andExpect(jsonPath("$.cloud.answered").value(20))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value("q01"))
                .andExpect(jsonPath("$.items[0].retrievalHit").value(true));
    }
}
