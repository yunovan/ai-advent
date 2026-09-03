package com.yunovan.aiadvent.day03;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.llm.LlmException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day03CompareController.class, ApiExceptionHandler.class})
class Day03CompareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day03ReasoningService reasoningService;

    @Test
    void postCompareReturnsFourMethods() throws Exception {
        when(reasoningService.solve("Задача"))
                .thenReturn(new ReasoningResponse(
                        "Задача",
                        "openai/gpt-4o-mini",
                        new MethodResult("direct", "Прямой ответ", "Задача", null, "10", "stop"),
                        new MethodResult("step-by-step", "Решай пошагово", "Задача\n\nРешай пошагово.", null, "5", "stop"),
                        new MethodResult(
                                "meta-prompt",
                                "Сначала промпт",
                                "generated + task",
                                "Реши через уравнения",
                                "5",
                                "stop"),
                        new MethodResult("experts", "Группа экспертов", "experts prompt", null, "аналитик: 5", "stop")));

        mockMvc.perform(post("/api/day3/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Задача\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direct.content").value("10"))
                .andExpect(jsonPath("$.stepByStep.content").value("5"))
                .andExpect(jsonPath("$.metaPrompt.generatedPrompt").value("Реши через уравнения"))
                .andExpect(jsonPath("$.experts.content").value("аналитик: 5"));
    }

    @Test
    void postCompareRejectsBlankPrompt() throws Exception {
        when(reasoningService.solve("   ")).thenThrow(new IllegalArgumentException("prompt must not be blank"));

        mockMvc.perform(post("/api/day3/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("prompt must not be blank"));
    }

    @Test
    void postCompareMapsLlmFailure() throws Exception {
        when(reasoningService.solve(any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day3/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }
}
