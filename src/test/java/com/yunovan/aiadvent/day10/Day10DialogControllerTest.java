package com.yunovan.aiadvent.day10;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.DialogMemory;
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

@WebMvcTest(controllers = {Day10DialogController.class, ApiExceptionHandler.class})
class Day10DialogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day10DialogService service;

    private static final Day10Branch MAIN = Day10Branch.main();

    @Test
    void postDialogsStartsSlidingWindowDialog() throws Exception {
        when(service.start("sliding", 6)).thenReturn(new Day10StartResponse(
                "d1", Instant.now(), Day10Strategy.SLIDING_WINDOW, 6,
                List.of(), List.of(MAIN), "main", List.of()));

        mockMvc.perform(post("/api/day10/dialogs")
                        .param("strategy", "sliding")
                        .param("windowSize", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogId").value("d1"))
                .andExpect(jsonPath("$.strategy").value("sliding"))
                .andExpect(jsonPath("$.windowSize").value(6));
    }

    @Test
    void postChatSendsFactsForFactsStrategy() throws Exception {
        when(service.chat("d1", "Привет", 1000L, 4)).thenReturn(new Day10ChatResponse(
                "d1", "Привет", "Привет, Ася!", "gpt-4o-mini", 2, 120L,
                Day10Strategy.FACTS, 4,
                List.of(new Day10Fact("Цель", "собрать ТЗ", true)),
                List.of(MAIN), "main",
                10, 5, 2, 2, 17, 20, 3, 8,
                new BigDecimal("0.00001"), new BigDecimal("0.00002"), new BigDecimal("0.00003"),
                1000L, false,
                List.of(ConversationMessage.user("Привет"), ConversationMessage.assistant("Привет, Ася!")),
                List.of(new DialogMemory("d0", "старый диалог"))));

        mockMvc.perform(post("/api/day10/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Привет\",\"contextLimit\":1000,\"windowSize\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogId").value("d1"))
                .andExpect(jsonPath("$.strategy").value("facts"))
                .andExpect(jsonPath("$.facts[0].key").value("Цель"))
                .andExpect(jsonPath("$.facts[0].value").value("собрать ТЗ"))
                .andExpect(jsonPath("$.historyTokens").value(2))
                .andExpect(jsonPath("$.savedTokens").value(3))
                .andExpect(jsonPath("$.exceeded").value(false));
    }

    @Test
    void getChatPassesParams() throws Exception {
        when(service.chat("d1", "Как дела?", 50L, 2)).thenReturn(new Day10ChatResponse(
                "d1", "Как дела?", "норм", null, 9, 0L,
                Day10Strategy.BRANCHING, 6,
                List.of(), List.of(MAIN), "main",
                0, 1, 0, 2, 3, 3, 0, 0,
                null, null, null,
                50L, true,
                List.of(ConversationMessage.user("Как дела?")),
                List.of()));

        mockMvc.perform(get("/api/day10/dialogs/d1/chat")
                        .param("request", "Как дела?")
                        .param("contextLimit", "50")
                        .param("windowSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("branching"))
                .andExpect(jsonPath("$.contextLimit").value(50))
                .andExpect(jsonPath("$.exceeded").value(true));
    }

    @Test
    void postChatMapsLlmFailure() throws Exception {
        when(service.chat(any(), any(), any(), any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day10/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }

    @Test
    void postChatMapsUnknownDialogToNotFound() throws Exception {
        when(service.chat(any(), any(), any(), any())).thenThrow(new DialogNotFoundException("d1"));

        mockMvc.perform(post("/api/day10/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Диалог 'd1' не найден"));
    }

    @Test
    void factsEndpointUpsertsFact() throws Exception {
        when(service.addFact("d1", "Стек", "Java 21", true)).thenReturn(new Day10DialogInfo(
                "d1", Instant.now(), null, null,
                Day10Strategy.FACTS, 4,
                List.of(new Day10Fact("Стек", "Java 21", true)),
                List.of(MAIN), "main", 0, List.of()));

        mockMvc.perform(post("/api/day10/dialogs/d1/facts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"key\":\"Стек\",\"value\":\"Java 21\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facts[0].key").value("Стек"))
                .andExpect(jsonPath("$.facts[0].active").value(true));
    }

    @Test
    void checkpointAndBranchAndActivateEndpointsWork() throws Exception {
        when(service.checkpoint("d1")).thenReturn(new Day10DialogInfo(
                "d1", Instant.now(), null, null,
                Day10Strategy.BRANCHING, 6,
                List.of(), List.of(MAIN), "main", 4, List.of()));
        when(service.createBranch("d1")).thenReturn(new Day10DialogInfo(
                "d1", Instant.now(), null, null,
                Day10Strategy.BRANCHING, 6,
                List.of(), List.of(MAIN), "b2", 4, List.of()));
        when(service.switchBranch("d1", "main")).thenReturn(new Day10DialogInfo(
                "d1", Instant.now(), null, null,
                Day10Strategy.BRANCHING, 6,
                List.of(), List.of(MAIN), "main", 4, List.of()));

        mockMvc.perform(post("/api/day10/dialogs/d1/checkpoint"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageCount").value(4));
        mockMvc.perform(post("/api/day10/dialogs/d1/branches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBranchId").value("b2"));
        mockMvc.perform(post("/api/day10/dialogs/d1/branches/main/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeBranchId").value("main"));
    }

    @Test
    void dialogsEndpointListsSummaries() throws Exception {
        when(service.dialogs()).thenReturn(List.of(new Day10DialogSummary(
                "d1", Instant.now(), Instant.now(), "Итог",
                Day10Strategy.SLIDING_WINDOW, 6, 12, 0, 1,
                150, 300, new BigDecimal("0.0002"), new BigDecimal("0.0004"))));

        mockMvc.perform(get("/api/day10/dialogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].strategy").value("sliding"))
                .andExpect(jsonPath("$[0].totalTokens").value(150))
                .andExpect(jsonPath("$[0].fullTotalTokens").value(300));
    }

    @Test
    void metricsEndpointReturnsGrowthReport() throws Exception {
        when(service.metrics("d1")).thenReturn(new Day10GrowthReport(
                "d1", Day10Strategy.FACTS, 128_000L,
                new BigDecimal("0.15"), new BigDecimal("0.60"),
                150, 200,
                new BigDecimal("0.0001"), new BigDecimal("0.00012"),
                4, 2, 1, "main",
                List.of(new Day10GrowthTurn(
                        1, 60, 90, 150, new BigDecimal("0.00003"), new BigDecimal("0.0001")))));

        mockMvc.perform(get("/api/day10/dialogs/d1/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.strategy").value("facts"))
                .andExpect(jsonPath("$.totalTokens").value(150))
                .andExpect(jsonPath("$.fullTotalTokens").value(200))
                .andExpect(jsonPath("$.factsCount").value(2))
                .andExpect(jsonPath("$.turns[0].promptTokens").value(60));
    }

    @Test
    void postFinishReturnsSummary() throws Exception {
        when(service.finish("d1")).thenReturn(new Day10FinishResponse("d1", Instant.now(), "Итог диалога", 12));

        mockMvc.perform(post("/api/day10/dialogs/d1/finish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Итог диалога"));
    }
}