package com.yunovan.aiadvent.day29;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.day26.Day26LlmException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day29Controller.class, ApiExceptionHandler.class})
class Day29ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day29OptimizationService service;

    private static Day29HealthResponse health() {
        return new Day29HealthResponse(
                new Day29ModelReport("http://localhost:11434", "qwen2.5:3b", true, true,
                        "0.35.1", "gguf", "3.1B", "Q4_K_M", 3_085_938_688L, 32_768L,
                        1_998_578_976L, 2_047_774_554L, ""),
                new Day29ProfileInfo("baseline", "Базовый (день 28)", 0.2, 300, null,
                        "day28", "параметры дня 28"),
                new Day29ProfileInfo("tuned", "Оптимизированный", 0.1, 240, 2048,
                        "compact-rag", "компактный шаблон"),
                1, 10, "локальный: без сетевых вызовов");
    }

    private static Day29AnswerResponse answer(String profileId, long latencyMs, int outTokens) {
        return new Day29AnswerResponse(profileId, "Профиль " + profileId,
                "Что такое эмбеддинги?", "Ответ по контексту", false, 50.0,
                java.util.List.of("embeddings.md"), "эмбеддинги", latencyMs, 300, outTokens,
                8.5, "");
    }

    private static Day29AskResponse ask() {
        return new Day29AskResponse("Что такое эмбеддинги?",
                java.util.List.of("embeddings.md"), 2200, 900,
                answer("baseline", 30_000L, 170), answer("tuned", 20_000L, 120),
                "Качество: оптимизированный профиль опирается на контекст сильнее (50.0% против 50.0%).",
                "Скорость: оптимизированный профиль быстрее на 10000 мс.");
    }

    private static Day29RunResponse run() {
        return new Day29RunResponse(10, 1,
                new Day29ProfileStats("baseline", "Базовый", 10, 0, 76.7, 40.0,
                        27_000.0, 9.0, 520.0, 170.0),
                new Day29ProfileStats("tuned", "Оптимизированный", 10, 0, 75.0, 45.0,
                        18_000.0, 9.5, 310.0, 120.0),
                2_047_774_554L,
                "Качество (покрытие ключевых слов): базовый 76.7% против оптимизированного 75.0%.",
                "Скорость: базовый профиль 27000 мс, оптимизированный 18000 мс.",
                "Ресурсы: входных токенов на запрос — базовый 520.0 против оптимизированного 310.0.");
    }

    @Test
    void healthReturnsModelReportAndProfiles() throws Exception {
        when(service.health()).thenReturn(health());

        mockMvc.perform(get("/api/day29/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.model.available").value(true))
                .andExpect(jsonPath("$.model.installed").value(true))
                .andExpect(jsonPath("$.model.version").value("0.35.1"))
                .andExpect(jsonPath("$.model.quantizationLevel").value("Q4_K_M"))
                .andExpect(jsonPath("$.model.parameterSize").value("3.1B"))
                .andExpect(jsonPath("$.model.contextLength").value(32768))
                .andExpect(jsonPath("$.model.loadedMemoryBytes").value(2047774554))
                .andExpect(jsonPath("$.baseline.id").value("baseline"))
                .andExpect(jsonPath("$.baseline.temperature").value(0.2))
                .andExpect(jsonPath("$.baseline.numCtx").doesNotExist())
                .andExpect(jsonPath("$.tuned.id").value("tuned"))
                .andExpect(jsonPath("$.tuned.numCtx").value(2048))
                .andExpect(jsonPath("$.benchmarkRuns").value(1))
                .andExpect(jsonPath("$.questionsLimit").value(10));
    }

    @Test
    void askReturnsBothProfilesWithVerdicts() throws Exception {
        when(service.ask("Что такое эмбеддинги?")).thenReturn(ask());

        mockMvc.perform(post("/api/day29/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Что такое эмбеддинги?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sources[0]").value("embeddings.md"))
                .andExpect(jsonPath("$.promptCharsBaseline").value(2200))
                .andExpect(jsonPath("$.promptCharsTuned").value(900))
                .andExpect(jsonPath("$.baseline.profileId").value("baseline"))
                .andExpect(jsonPath("$.baseline.answer").value("Ответ по контексту"))
                .andExpect(jsonPath("$.tuned.profileId").value("tuned"))
                .andExpect(jsonPath("$.tuned.latencyMs").value(20000))
                .andExpect(jsonPath("$.qualityVerdict").value(
                        "Качество: оптимизированный профиль опирается на контекст сильнее (50.0% против 50.0%)."))
                .andExpect(jsonPath("$.speedVerdict").value(
                        "Скорость: оптимизированный профиль быстрее на 10000 мс."));
    }

    @Test
    void askWithBlankQuestionReturnsBadRequest() throws Exception {
        when(service.ask(anyString()))
                .thenThrow(new IllegalArgumentException("Вопрос не может быть пустым"));

        mockMvc.perform(post("/api/day29/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Вопрос не может быть пустым"));
    }

    @Test
    void askWithMissingQuestionReturnsBadRequest() throws Exception {
        when(service.ask(null))
                .thenThrow(new IllegalArgumentException("Вопрос не может быть пустым"));

        mockMvc.perform(post("/api/day29/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void askWhenLocalLlmProbeFailsReturnsBadGateway() throws Exception {
        when(service.ask(any()))
                .thenThrow(new Day26LlmException("Локальный LLM недоступен на http://localhost:11434"));

        mockMvc.perform(post("/api/day29/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Вопрос\"}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void runReturnsAggregatedStatsAndVerdicts() throws Exception {
        when(service.run()).thenReturn(run());

        mockMvc.perform(post("/api/day29/run")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions").value(10))
                .andExpect(jsonPath("$.runs").value(1))
                .andExpect(jsonPath("$.baseline.answers").value(10))
                .andExpect(jsonPath("$.baseline.avgCoveragePercent").value(76.7))
                .andExpect(jsonPath("$.tuned.answers").value(10))
                .andExpect(jsonPath("$.tuned.avgLatencyMs").value(18000))
                .andExpect(jsonPath("$.loadedMemoryBytes").value(2047774554))
                .andExpect(jsonPath("$.qualityVerdict").value(
                        "Качество (покрытие ключевых слов): базовый 76.7% против оптимизированного 75.0%."))
                .andExpect(jsonPath("$.speedVerdict").isNotEmpty())
                .andExpect(jsonPath("$.resourceVerdict").isNotEmpty());
    }
}
