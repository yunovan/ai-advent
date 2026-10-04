package com.yunovan.aiadvent.day25;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.day21.Day21IndexException;
import com.yunovan.aiadvent.day24.Day24Quote;
import com.yunovan.aiadvent.day24.Day24Source;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day25Controller.class, ApiExceptionHandler.class})
class Day25ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day25ChatService service;

    private Day25ChatTurn turn() {
        return new Day25ChatTurn("s1", 1, "Что такое эмбеддинги документов?",
                "В документах говорится: «Эмбеддинги — это векторы…»",
                "Что такое эмбеддинги документов?", false, 10, 2, 0.9,
                List.of(new Day24Source("articles/embeddings.md", "Признаковые алгоритмы",
                        "articles/embeddings.md#fixed#0001", 0.9, "сниппет")),
                List.of(new Day24Quote("articles/embeddings.md", "Признаковые алгоритмы",
                        "articles/embeddings.md#fixed#0001",
                        "Эмбеддинги — это векторы, а n-граммы становятся признаками.",
                        0.9, 2)),
                100.0, true, false, true,
                new Day25TaskMemory("разобраться в эмбеддингах", 1, 1,
                        List.of("только по документации"), List.of("без выдумок"),
                        List.of("эмбеддинг")),
                List.of(new Day25Message("user", "Что такое эмбеддинги документов?", 1),
                        new Day25Message("assistant", "В документах говорится: …", 1)),
                2);
    }

    @Test
    void getHealthReturnsChatConfiguration() throws Exception {
        when(service.health()).thenReturn(new Day25HealthResponse(9, 131000, 44, "fixed",
                10, 5, 0.5, 0.32, true, 3, 24, 0.35, 400, 40, 200, 8, 1,
                List.of("Эмбеддинги: от вектора до признаков"), List.of("fixed")));

        mockMvc.perform(get("/api/day25/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").value(9))
                .andExpect(jsonPath("$.historyLimit").value(40))
                .andExpect(jsonPath("$.maxSessions").value(200))
                .andExpect(jsonPath("$.memoryTermsLimit").value(8))
                .andExpect(jsonPath("$.scenarios[0]").value("Эмбеддинги: от вектора до признаков"));
    }

    @Test
    void postChatReturnsReplySourcesAndMemory() throws Exception {
        when(service.chat(any(), any())).thenReturn(turn());

        mockMvc.perform(post("/api/day25/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s1\",\"message\":\"Что такое эмбеддинги документов?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.turn").value(1))
                .andExpect(jsonPath("$.sources[0].source").value("articles/embeddings.md"))
                .andExpect(jsonPath("$.sources[0].chunkId").value("articles/embeddings.md#fixed#0001"))
                .andExpect(jsonPath("$.quotes[0].text").exists())
                .andExpect(jsonPath("$.memory.goal").value("разобраться в эмбеддингах"))
                .andExpect(jsonPath("$.memory.constraints[0]").value("без выдумок"))
                .andExpect(jsonPath("$.historySize").value(2));
    }

    @Test
    void blankMessageReturnsBadRequest() throws Exception {
        when(service.chat(any(), any()))
                .thenThrow(new IllegalArgumentException("Сообщение не может быть пустым"));

        mockMvc.perform(post("/api/day25/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s1\",\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void indexFailureReturnsServerError() throws Exception {
        when(service.chat(any(), any()))
                .thenThrow(new Day21IndexException("Индекс не построен"));

        mockMvc.perform(post("/api/day25/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s1\",\"message\":\"вопрос\"}"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void getHistoryReturnsStoredMessages() throws Exception {
        when(service.history(eq("s1"))).thenReturn(List.of(
                new Day25Message("user", "Что такое эмбеддинги документов?", 1),
                new Day25Message("assistant", "В документах говорится: …", 1)));

        mockMvc.perform(get("/api/day25/history").param("sessionId", "s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[1].role").value("assistant"));
    }

    @Test
    void postResetClearsSession() throws Exception {
        when(service.memory(any())).thenReturn(new Day25SessionView("s1", 0, 0, null, null, 0, 0, 0));

        mockMvc.perform(post("/api/day25/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"s1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("s1"))
                .andExpect(jsonPath("$.goal").doesNotExist());
    }

    @Test
    void getScenariosReturnsTwoLongDialogs() throws Exception {
        when(service.scenarios()).thenReturn(Day25Scenarios.ALL);

        mockMvc.perform(get("/api/day25/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].messages.length()").value(12))
                .andExpect(jsonPath("$[1].messages.length()").value(13));
    }
}
