package com.yunovan.aiadvent.day08;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.llm.LlmException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day08TokenController.class, com.yunovan.aiadvent.day01.ApiExceptionHandler.class})
class Day08TokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day08TokenService service;

    @Test
    void postChatReturnsTokenMetrics() throws Exception {
        Day08ChatResponse response = new Day08ChatResponse(
                "default",
                "Привет",
                "Привет, Ася!",
                "gpt-4o-mini",
                3,
                120L,
                10,
                0,
                65,
                7,
                12,
                8,
                20,
                new BigDecimal("0.0001"),
                new BigDecimal("0.00005"),
                new BigDecimal("0.00012"),
                128_000L,
                false,
                List.of());
        when(service.chat("default", "Привет", 1000L)).thenReturn(response);

        mockMvc.perform(post("/api/day8/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"default\",\"request\":\"Привет\",\"contextLimit\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("default"))
                .andExpect(jsonPath("$.requestTokens").value(10))
                .andExpect(jsonPath("$.promptTokens").value(65))
                .andExpect(jsonPath("$.exceeded").value(false))
                .andExpect(jsonPath("$.realTotalTokens").value(20));
    }

    @Test
    void getChatPassesContextLimitAndSession() throws Exception {
        when(service.chat("alice", "Как дела?", 50L))
                .thenReturn(new Day08ChatResponse(
                        "alice", "Как дела?", "норм", null, 5, 0L, 1, 0, 2, 3, null, null, null, null,
                        null, null, 50L, false, List.of()));

        mockMvc.perform(get("/api/day8/chat")
                        .param("request", "Как дела?")
                        .param("sessionId", "alice")
                        .param("contextLimit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("alice"))
                .andExpect(jsonPath("$.contextLimit").value(50));
    }

    @Test
    void postChatReturnsExceededFlagWhenLimitHit() throws Exception {
        when(service.chat(any(), any(), any())).thenReturn(new Day08ChatResponse(
                "default", "q", "превысил контекстное окно", null, 5, 0L, 1, 0, 200, 0, null, null, null,
                null, null, null, 100L, true, List.of()));

        mockMvc.perform(post("/api/day8/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"default\",\"request\":\"q\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceeded").value(true))
                .andExpect(jsonPath("$.content").value("превысил контекстное окно"));
    }

    @Test
    void postChatMapsLlmFailure() throws Exception {
        when(service.chat(any(), any(), any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day8/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"default\",\"request\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }

    @Test
    void metricsEndpointReturnsGrowthReport() throws Exception {
        when(service.metrics("default")).thenReturn(new Day08GrowthReport(
                "default",
                128_000L,
                new BigDecimal("0.15"),
                new BigDecimal("0.60"),
                150,
                new BigDecimal("0.0001"),
                List.of(new Day08GrowthTurn(1, 80, 20, 100, new BigDecimal("0.00003"), new BigDecimal("0.00003")))));

        mockMvc.perform(get("/api/day8/metrics").param("sessionId", "default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTokens").value(150))
                .andExpect(jsonPath("$.turns[0].promptTokens").value(80))
                .andExpect(jsonPath("$.turns[0].turn").value(1));
    }

    @Test
    void postResetDeletesHistory() throws Exception {
        mockMvc.perform(post("/api/day8/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"default\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("default"))
                .andExpect(jsonPath("$.message").exists());

        verify(service).reset("default");
    }
}