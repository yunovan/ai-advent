package com.yunovan.aiadvent.day11;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.agent.ConversationMessage;
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

@WebMvcTest(controllers = {Day11DialogController.class, ApiExceptionHandler.class})
class Day11DialogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day11DialogService service;

    private static Day11DialogInfo info(String dialogId) {
        return new Day11DialogInfo(
                dialogId, Instant.now(), null, null, 0, List.of(),
                List.of(new Day11MemoryEntry("Цель", "собрать ТЗ", Day11MemoryLayer.SHORT_TERM, "extracted", true, null)),
                List.of(new Day11MemoryEntry("Бюджет", "10 000$", Day11MemoryLayer.WORKING, "manual", false, null)),
                List.of(new Day11MemoryEntry("Имя", "Ася", Day11MemoryLayer.LONG_TERM, "manual", false, null)));
    }

    @Test
    void postDialogsStartsDialog() throws Exception {
        when(service.start()).thenReturn(new Day11StartResponse("d1", Instant.now(), Day11MemoryLayer.values()));

        mockMvc.perform(post("/api/day11/dialogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogId").value("d1"))
                .andExpect(jsonPath("$.layers[0]").value("short-term"))
                .andExpect(jsonPath("$.layers[1]").value("working"))
                .andExpect(jsonPath("$.layers[2]").value("long-term"));
    }

    @Test
    void postChatReturnsPerLayerMetricsAndMemory() throws Exception {
        when(service.chat("d1", "Соберём ТЗ", 1000L)).thenReturn(new Day11ChatResponse(
                "d1", "Соберём ТЗ", "Отлично!", "gpt-4o-mini", 2, 120L,
                20, 5, 30, 10, 15, 8, 55, 1000L, false,
                List.of(ConversationMessage.user("Соберём ТЗ"), ConversationMessage.assistant("Отлично!")),
                List.of(new Day11MemoryEntry("Цель", "собрать ТЗ", Day11MemoryLayer.SHORT_TERM, "extracted", true, null)),
                List.of(),
                List.of(new Day11MemoryEntry("Имя", "Ася", Day11MemoryLayer.LONG_TERM, "manual", false, null))));

        mockMvc.perform(post("/api/day11/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Соберём ТЗ\",\"contextLimit\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogId").value("d1"))
                .andExpect(jsonPath("$.shortTermTokens").value(30))
                .andExpect(jsonPath("$.workingTokens").value(10))
                .andExpect(jsonPath("$.longTermTokens").value(15))
                .andExpect(jsonPath("$.candidates[0].key").value("Цель"))
                .andExpect(jsonPath("$.longTerm[0].value").value("Ася"))
                .andExpect(jsonPath("$.exceeded").value(false));
    }

    @Test
    void getChatPassesContextLimit() throws Exception {
        when(service.chat("d1", "Как дела?", 50L)).thenReturn(new Day11ChatResponse(
                "d1", "Как дела?", "норм", null, 9, 0L,
                5, 1, 2, 3, 4, 0, 60, 50L, true,
                List.of(ConversationMessage.user("Как дела?")), List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/day11/dialogs/d1/chat").param("request", "Как дела?").param("contextLimit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contextLimit").value(50))
                .andExpect(jsonPath("$.exceeded").value(true));
    }

    @Test
    void postChatMapsLlmFailure() throws Exception {
        when(service.chat(any(), any(), any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day11/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }

    @Test
    void postChatMapsUnknownDialogToNotFound() throws Exception {
        when(service.chat(any(), any(), any())).thenThrow(new DialogNotFoundException("d1"));

        mockMvc.perform(post("/api/day11/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Диалог 'd1' не найден"));
    }

    @Test
    void rememberEndpointSavesIntoChosenLayer() throws Exception {
        when(service.remember("d1", "Стек", "Java 21", "long-term")).thenReturn(info("d1"));

        mockMvc.perform(post("/api/day11/dialogs/d1/remember")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"Стек\",\"value\":\"Java 21\",\"layer\":\"long-term\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.longTerm[0].value").value("Ася"));
    }

    @Test
    void promoteEndpointMovesCandidate() throws Exception {
        when(service.promote("d1", "Цель", "working")).thenReturn(info("d1"));

        mockMvc.perform(post("/api/day11/dialogs/d1/promote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"Цель\",\"toLayer\":\"working\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].key").value("Цель"));
    }

    @Test
    void decideEndpointRecordsLongTermDecision() throws Exception {
        when(service.decide("d1", "Берём Java")).thenReturn(info("d1"));

        mockMvc.perform(post("/api/day11/dialogs/d1/decide")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"statement\":\"Берём Java\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void forgetEndpointDeletesEntry() throws Exception {
        when(service.forget("d1", "short-term", "Цель")).thenReturn(info("d1"));

        mockMvc.perform(post("/api/day11/dialogs/d1/forget")
                        .param("layer", "short-term")
                        .param("key", "Цель"))
                .andExpect(status().isOk());
    }

    @Test
    void metricsEndpointReturnsGrowthReport() throws Exception {
        when(service.metrics("d1")).thenReturn(new Day11MetricsReport(
                "d1", 128_000L,
                new BigDecimal("0.15"), new BigDecimal("0.60"),
                150, 200,
                new BigDecimal("0.0001"), new BigDecimal("0.00012"),
                6, 2, 1,
                List.of(new Day11MetricsTurn(
                        1, 60, 150, 200,
                        new BigDecimal("0.00003"), new BigDecimal("0.0001"), new BigDecimal("0.00012")))));

        mockMvc.perform(get("/api/day11/dialogs/d1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTokens").value(150))
                .andExpect(jsonPath("$.fullTotalTokens").value(200))
                .andExpect(jsonPath("$.workingCount").value(2))
                .andExpect(jsonPath("$.turns[0].promptTokens").value(60));
    }

    @Test
    void postFinishReturnsSummary() throws Exception {
        when(service.finish("d1")).thenReturn(new Day11FinishResponse("d1", Instant.now(), "Итог диалога", 6, 3));

        mockMvc.perform(post("/api/day11/dialogs/d1/finish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Итог диалога"))
                .andExpect(jsonPath("$.longTermEntryCount").value(3));
    }
}