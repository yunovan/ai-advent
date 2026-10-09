package com.yunovan.aiadvent.day27;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.day26.Day26LlmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day27Controller.class, ApiExceptionHandler.class})
class Day27ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day27ChatService service;

    private static Day27HealthResponse health() {
        return new Day27HealthResponse("http://localhost:11434", "qwen2.5:3b", true,
                "0.35.1", true, false, 40, 50, 2, "");
    }

    private static Day27ChatTurn turn() {
        return new Day27ChatTurn("web", 1, "Привет!", "Здравствуйте!", "qwen2.5:3b",
                "http://localhost:11434", 3_100, 45, 25, 5.3,
                List.of(new Day27Message("user", "Привет!", 1, 0, 0, 0, 0.0),
                        new Day27Message("assistant", "Здравствуйте!", 1, 3_100, 45, 25, 5.3)),
                2);
    }

    @Test
    void healthReturnsLocalConnectionStateWithoutCloud() throws Exception {
        when(service.health()).thenReturn(health());

        mockMvc.perform(get("/api/day27/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.model").value("qwen2.5:3b"))
                .andExpect(jsonPath("$.usesCloud").value(false))
                .andExpect(jsonPath("$.historyLimit").value(40))
                .andExpect(jsonPath("$.sessions").value(2));
    }

    @Test
    void historyReturnsStoredMessages() throws Exception {
        when(service.history("web")).thenReturn(
                List.of(new Day27Message("user", "Привет!", 1, 0, 0, 0, 0.0)));

        mockMvc.perform(get("/api/day27/history").param("sessionId", "web"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].text").value("Привет!"));
    }

    @Test
    void sessionsReturnViews() throws Exception {
        when(service.sessions()).thenReturn(
                List.of(new Day27SessionView("web", 1, 2, "Привет!")));

        mockMvc.perform(get("/api/day27/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sessionId").value("web"))
                .andExpect(jsonPath("$[0].turns").value(1));
    }

    @Test
    void chatReturnsTurnWithMetricsAndHistory() throws Exception {
        when(service.chat("web", "Привет!")).thenReturn(turn());

        mockMvc.perform(post("/api/day27/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"web\",\"message\":\"Привет!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("web"))
                .andExpect(jsonPath("$.turn").value(1))
                .andExpect(jsonPath("$.reply").value("Здравствуйте!"))
                .andExpect(jsonPath("$.promptTokens").value(45))
                .andExpect(jsonPath("$.outputTokens").value(25))
                .andExpect(jsonPath("$.tokensPerSecond").value(5.3))
                .andExpect(jsonPath("$.historySize").value(2))
                .andExpect(jsonPath("$.history[0].role").value("user"));
    }

    @Test
    void chatWithBlankMessageReturnsBadRequest() throws Exception {
        when(service.chat(anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("Сообщение не может быть пустым"));

        mockMvc.perform(post("/api/day27/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"web\",\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatWithMissingBodyReturnsBadRequest() throws Exception {
        when(service.chat(null, null))
                .thenThrow(new IllegalArgumentException("Сообщение не может быть пустым"));

        mockMvc.perform(post("/api/day27/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chatWhenLocalLlmIsDownReturnsBadGateway() throws Exception {
        when(service.chat("web", "Привет")).thenThrow(
                new Day26LlmException("Локальный LLM недоступен на http://localhost:11434"));

        mockMvc.perform(post("/api/day27/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"web\",\"message\":\"Привет\"}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void resetClearsSessionAndReturnsEmptyView() throws Exception {
        mockMvc.perform(post("/api/day27/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"web\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("web"))
                .andExpect(jsonPath("$.turns").value(0))
                .andExpect(jsonPath("$.historySize").value(0));
    }
}
