package com.yunovan.aiadvent.day26;

import static org.mockito.ArgumentMatchers.anyString;
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

@WebMvcTest(controllers = {Day26Controller.class, ApiExceptionHandler.class})
class Day26ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day26Service service;

    private static Day26HealthResponse health() {
        return new Day26HealthResponse("http://localhost:11434", "qwen2.5:3b", true,
                "0.35.1", true,
                List.of(new Day26InstalledModel("qwen2.5:3b", 1_998_578_976L, "3.09B", "Q4_K_M")),
                "");
    }

    private static Day26RunReport report() {
        return new Day26RunReport("http://localhost:11434", "qwen2.5:3b",
                Day26Task.ALL.stream().map(task -> Day26TaskResult.ok(task, answer())).toList(),
                3, 0, 31_000, "все 3 запросов выполнены: локальная LLM запущена и отвечает");
    }

    private static Day26Answer answer() {
        return new Day26Answer("Привет! Ответь одной фразой: кто ты такой?",
                "Я Qwen, языковая модель от Alibaba Cloud.", "qwen2.5:3b",
                "http://localhost:11434", 3_100, 45, 11, 5.3);
    }

    @Test
    void healthReturnsServerStateAndModels() throws Exception {
        when(service.health()).thenReturn(health());

        mockMvc.perform(get("/api/day26/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.version").value("0.35.1"))
                .andExpect(jsonPath("$.model").value("qwen2.5:3b"))
                .andExpect(jsonPath("$.modelInstalled").value(true))
                .andExpect(jsonPath("$.installedModels[0].name").value("qwen2.5:3b"))
                .andExpect(jsonPath("$.installedModels[0].parameterSize").value("3.09B"));
    }

    @Test
    void tasksReturnThreeComplexityLevels() throws Exception {
        when(service.tasks()).thenReturn(Day26Task.ALL);

        mockMvc.perform(get("/api/day26/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value("simple"))
                .andExpect(jsonPath("$[1].id").value("medium"))
                .andExpect(jsonPath("$[2].id").value("complex"));
    }

    @Test
    void runReturnsReportWithVerdict() throws Exception {
        when(service.run()).thenReturn(report());

        mockMvc.perform(post("/api/day26/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.okCount").value(3))
                .andExpect(jsonPath("$.failureCount").value(0))
                .andExpect(jsonPath("$.totalLatencyMs").value(31_000))
                .andExpect(jsonPath("$.verdict")
                        .value("все 3 запросов выполнены: локальная LLM запущена и отвечает"));
    }

    @Test
    void askReturnsAnswerWithTokenMetrics() throws Exception {
        when(service.ask(anyString())).thenReturn(answer());

        mockMvc.perform(post("/api/day26/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Привет! Ответь одной фразой: кто ты такой?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Я Qwen, языковая модель от Alibaba Cloud."))
                .andExpect(jsonPath("$.promptTokens").value(45))
                .andExpect(jsonPath("$.outputTokens").value(11))
                .andExpect(jsonPath("$.tokensPerSecond").value(5.3));
    }

    @Test
    void askWithBlankPromptReturnsBadRequest() throws Exception {
        when(service.ask("   ")).thenThrow(new IllegalArgumentException("Запрос не может быть пустым"));

        mockMvc.perform(post("/api/day26/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void askWithMissingBodyReturnsBadRequest() throws Exception {
        when(service.ask(null)).thenThrow(new IllegalArgumentException("Запрос не может быть пустым"));

        mockMvc.perform(post("/api/day26/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void askWhenLocalLlmIsDownReturnsBadGateway() throws Exception {
        when(service.ask("вопрос")).thenThrow(
                new Day26LlmException("Локальный LLM недоступен на http://localhost:11434"));

        mockMvc.perform(post("/api/day26/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"вопрос\"}"))
                .andExpect(status().isBadGateway());
    }
}
