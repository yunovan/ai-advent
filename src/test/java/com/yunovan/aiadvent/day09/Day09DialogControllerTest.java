package com.yunovan.aiadvent.day09;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.agent.dialog.DialogNotFoundException;
import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.llm.LlmException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day09DialogController.class, ApiExceptionHandler.class})
class Day09DialogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day09DialogService service;

    @Test
    void postDialogsStartsDialog() throws Exception {
        when(service.start()).thenReturn(new Day09StartResponse("d1", Instant.now(), List.of(), List.of()));

        mockMvc.perform(post("/api/day9/dialogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogId").value("d1"));
    }

    @Test
    void postChatReturnsCompressionMetrics() throws Exception {
        when(service.chat("d1", "Привет", 1000L, true)).thenReturn(new Day09ChatResponse(
                "d1", "Привет", "Привет, Ася!", "gpt-4o-mini", 2, 120L,
                true, 0,
                3, 10, 0, 0, 0, 65, 65, 0, 8,
                null, null, 20, new BigDecimal("0.0001"),
                new BigDecimal("0.0001"), new BigDecimal("0.00005"), new BigDecimal("0.00012"),
                1000L, false, List.of(), List.of()));

        mockMvc.perform(post("/api/day9/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Привет\",\"contextLimit\":1000,\"compression\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogId").value("d1"))
                .andExpect(jsonPath("$.compression").value(true))
                .andExpect(jsonPath("$.requestTokens").value(10))
                .andExpect(jsonPath("$.contextTokens").value(3))
                .andExpect(jsonPath("$.promptTokens").value(65))
                .andExpect(jsonPath("$.savedTokens").value(0))
                .andExpect(jsonPath("$.exceeded").value(false));
    }

    @Test
    void getChatPassesContextLimitAndCompression() throws Exception {
        when(service.chat("d1", "Как дела?", 50L, false)).thenReturn(new Day09ChatResponse(
                "d1", "Как дела?", "норм", null, 5, 0L,
                false, 2,
                0, 1, 0, 2, 3,
                0, 0, 0, 0,
                null, null, null, null,
                null, null, null,
                50L, true, List.of(), List.of()));

        mockMvc.perform(get("/api/day9/dialogs/d1/chat")
                        .param("request", "Как дела?")
                        .param("contextLimit", "50")
                        .param("compression", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextLimit").value(50))
                .andExpect(jsonPath("$.compression").value(false))
                .andExpect(jsonPath("$.exceeded").value(true));
    }

    @Test
    void postChatMapsLlmFailure() throws Exception {
        when(service.chat(any(), any(), any(), any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day9/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }

    @Test
    void postChatMapsUnknownDialogToNotFound() throws Exception {
        when(service.chat(any(), any(), any(), any())).thenThrow(new DialogNotFoundException("d1"));

        mockMvc.perform(post("/api/day9/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Диалог 'd1' не найден"));
    }

    @Test
    void metricsEndpointReturnsGrowthReport() throws Exception {
        when(service.metrics("d1")).thenReturn(new Day09GrowthReport(
                "d1",
                128_000L,
                new BigDecimal("0.15"),
                new BigDecimal("0.60"),
                150,
                200,
                50,
                new BigDecimal("0.0001"),
                new BigDecimal("0.00012"),
                List.of(new Day09GrowthTurn(3, 60, 90, 20, 150, 200,
                        new BigDecimal("0.00003"), new BigDecimal("0.0001"), new BigDecimal("0.00012")))));

        mockMvc.perform(get("/api/day9/dialogs/d1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogId").value("d1"))
                .andExpect(jsonPath("$.totalTokens").value(150))
                .andExpect(jsonPath("$.fullTotalTokens").value(200))
                .andExpect(jsonPath("$.savedTokens").value(50))
                .andExpect(jsonPath("$.turns[0].promptTokens").value(60))
                .andExpect(jsonPath("$.turns[0].fullPromptTokens").value(90))
                .andExpect(jsonPath("$.turns[0].turn").value(3));
    }

    @Test
    void postFinishReturnsSummary() throws Exception {
        when(service.finish("d1")).thenReturn(new Day09FinishResponse("d1", Instant.now(), "Итог диалога", 2));

        mockMvc.perform(post("/api/day9/dialogs/d1/finish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Итог диалога"));
    }
}