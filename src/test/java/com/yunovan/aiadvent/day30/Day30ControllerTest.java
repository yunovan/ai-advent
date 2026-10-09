package com.yunovan.aiadvent.day30;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day29.Day29ModelReport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day30Controller.class, ApiExceptionHandler.class})
class Day30ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day30PrivateLlmService service;

    private static Day30HealthResponse health() {
        return new Day30HealthResponse("ok", "http://localhost:8080", "localhost", 8080,
                List.of("http://localhost:8080", "http://192.168.1.10:8080"),
                new Day29ModelReport("http://localhost:11434", "qwen2.5:3b", true, true,
                        "0.35.1", "gguf", "3.1B", "Q4_K_M", 3_085_938_688L, 32_768L,
                        1_998_578_976L, 2_047_774_554L, ""),
                new Day30Limits(12, 6000, 10, 4, 300, 32_768L, false),
                new Day30Stats(0, 0, 0, 0, 0, 0, 0, 0, null, null));
    }

    private static Day30ChatResponse chat() {
        return new Day30ChatResponse("sess-1", 1, "Я Qwen, языковая модель от Alibaba Cloud.",
                2, 40, false, 1234L, 45, 25, 8.5, 9, "qwen2.5:3b");
    }

    private static Day30StressResponse stress() {
        return new Day30StressResponse(3, 3, 3, 0, 0, 900L, 3.3, 250.0, 200L, 300L,
                List.of(new Day30StressItem(1, true, false, 200L, 10, ""),
                        new Day30StressItem(2, true, false, 250L, 12, ""),
                        new Day30StressItem(3, true, false, 300L, 11, "")),
                "Сервис выдержал нагрузку без потерь.");
    }

    @Test
    void healthReturnsServiceModelAndLimits() throws Exception {
        when(service.health()).thenReturn(health());

        mockMvc.perform(get("/api/day30/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.serviceUrl").value("http://localhost:8080"))
                .andExpect(jsonPath("$.networkUrls[1]").value("http://192.168.1.10:8080"))
                .andExpect(jsonPath("$.model.available").value(true))
                .andExpect(jsonPath("$.model.quantizationLevel").value("Q4_K_M"))
                .andExpect(jsonPath("$.limits.rateLimitPerMinute").value(10))
                .andExpect(jsonPath("$.limits.maxConcurrent").value(4))
                .andExpect(jsonPath("$.limits.apiKeyRequired").value(false))
                .andExpect(jsonPath("$.stats.totalRequests").value(0));
    }

    @Test
    void chatReturnsReplyAndMetrics() throws Exception {
        when(service.chat(any(), any(), any())).thenReturn(chat());

        mockMvc.perform(post("/api/day30/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Кто ты?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sess-1"))
                .andExpect(jsonPath("$.turn").value(1))
                .andExpect(jsonPath("$.reply").value("Я Qwen, языковая модель от Alibaba Cloud."))
                .andExpect(jsonPath("$.contextMessages").value(2))
                .andExpect(jsonPath("$.rateRemaining").value(9))
                .andExpect(jsonPath("$.model").value("qwen2.5:3b"));
    }

    @Test
    void chatWithBlankMessageReturnsBadRequest() throws Exception {
        when(service.chat(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Сообщение не может быть пустым"));

        mockMvc.perform(post("/api/day30/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Сообщение не может быть пустым"));
    }

    @Test
    void chatWithoutApiKeyReturnsUnauthorized() throws Exception {
        when(service.chat(any(), any(), any()))
                .thenThrow(new Day30AuthException("Неверный или отсутствующий ключ доступа"));

        mockMvc.perform(post("/api/day30/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"привет\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void chatWhenRateLimitedReturnsTooManyRequests() throws Exception {
        when(service.chat(any(), any(), any()))
                .thenThrow(new Day30RateLimitException("Превышен лимит 10 запросов в минуту"));

        mockMvc.perform(post("/api/day30/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"привет\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void chatWhenLocalLlmFailsReturnsBadGateway() throws Exception {
        when(service.chat(any(), any(), any()))
                .thenThrow(new Day26LlmException("Локальный LLM недоступен"));

        mockMvc.perform(post("/api/day30/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"привет\"}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void stressReturnsAggregatedStats() throws Exception {
        when(service.stress(any(), any(), any())).thenReturn(stress());

        mockMvc.perform(post("/api/day30/stress")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requests").value(3))
                .andExpect(jsonPath("$.succeeded").value(3))
                .andExpect(jsonPath("$.failed").value(0))
                .andExpect(jsonPath("$.items[0].ok").value(true))
                .andExpect(jsonPath("$.verdict").value("Сервис выдержал нагрузку без потерь."));
    }
}
