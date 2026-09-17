package com.yunovan.aiadvent.day14;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.llm.LlmException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day14InvariantController.class, ApiExceptionHandler.class})
class Day14InvariantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day14InvariantService service;

    private static Day14Invariant invariant(String id, Day14Category category, String title, boolean active) {
        return new Day14Invariant(id, category, title, "только PostgreSQL", active, Instant.now());
    }

    private static Day14AdviseResponse advice(String content, long promptTokens, List<Day14Invariant> invariants) {
        return new Day14AdviseResponse(
                "c1", "Заменим на MySQL?", content, "gpt-4o-mini", 100L,
                20, 5, 0L, 6, promptTokens, 128000L, false, invariants);
    }

    @Test
    void postCreateCreatesInvariant() throws Exception {
        when(service.create("стек", "База данных", "только PostgreSQL"))
                .thenReturn(invariant("i1", Day14Category.STACK, "База данных", true));

        mockMvc.perform(post("/api/day14/invariants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"стек\",\"title\":\"База данных\",\"description\":\"только PostgreSQL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("i1"))
                .andExpect(jsonPath("$.category").value("STACK"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getInvariantsListsAll() throws Exception {
        when(service.list()).thenReturn(List.of(
                invariant("i1", Day14Category.STACK, "База данных", true),
                invariant("i2", Day14Category.BUSINESS, "Скидки", false)));

        mockMvc.perform(get("/api/day14/invariants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].category").value("STACK"))
                .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void getActiveReturnsOnlyActive() throws Exception {
        when(service.active()).thenReturn(List.of(invariant("i1", Day14Category.STACK, "База данных", true)));

        mockMvc.perform(get("/api/day14/invariants/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("i1"));
    }

    @Test
    void getInvariantReturnsIt() throws Exception {
        when(service.get("i1")).thenReturn(invariant("i1", Day14Category.STACK, "База данных", true));

        mockMvc.perform(get("/api/day14/invariants/i1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("База данных"))
                .andExpect(jsonPath("$.description").value("только PostgreSQL"));
    }

    @Test
    void postDeactivateMarksInactive() throws Exception {
        when(service.deactivate("i1")).thenReturn(invariant("i1", Day14Category.STACK, "База данных", false));

        mockMvc.perform(post("/api/day14/invariants/i1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void deleteRemovesInvariant() throws Exception {
        when(service.delete("i1")).thenReturn(true);

        mockMvc.perform(delete("/api/day14/invariants/i1"))
                .andExpect(status().isOk());
    }

    @Test
    void postAdviseReturnsAgentReplyAndInvariants() throws Exception {
        List<Day14Invariant> invariants = List.of(invariant("i1", Day14Category.STACK, "База данных", true));
        when(service.advise("Заменим на MySQL?", null))
                .thenReturn(advice("Не могу предложить MySQL: нарушен инвариант стека", 31, invariants));

        mockMvc.perform(post("/api/day14/advise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Заменим на MySQL?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(containsString("Не могу предложить MySQL")))
                .andExpect(jsonPath("$.promptTokens").value(31))
                .andExpect(jsonPath("$.exceeded").value(false))
                .andExpect(jsonPath("$.invariants[0].title").value("База данных"));
    }

    @Test
    void postAdviseMapsLlmFailureToBadGateway() throws Exception {
        when(service.advise(any(), any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day14/advise")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }

    @Test
    void postAdviseMapsUnknownInvariantToNotFound() throws Exception {
        when(service.get("nope")).thenThrow(new Day14InvariantNotFoundException("nope"));

        mockMvc.perform(get("/api/day14/invariants/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Инвариант 'nope' не найден"));
    }

    @Test
    void postCreateMapsIllegalArgumentToBadRequest() throws Exception {
        when(service.create(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Неизвестная категория инварианта: космос"));

        mockMvc.perform(post("/api/day14/invariants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"космос\",\"title\":\"База\",\"description\":\"описание\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Неизвестная категория инварианта: космос"));
    }
}