package com.yunovan.aiadvent.day02;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.llm.LlmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day02CompareController.class, ApiExceptionHandler.class})
class Day02CompareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day02CompareService compareService;

    @Test
    void postCompareReturnsBothAnswers() throws Exception {
        when(compareService.compare("Что такое ИИ?"))
                .thenReturn(new CompareResponse(
                        "Что такое ИИ?",
                        "openai/gpt-4o-mini",
                        new CompareSample("Длинный текст", "stop", 13, 2),
                        new CompareResponse.ConstrainedSample(
                                "1. Коротко.",
                                "stop",
                                11,
                                2,
                                Day02Constraints.SYSTEM_PROMPT.trim(),
                                80,
                                List.of("<<<END>>>"))));

        mockMvc.perform(post("/api/day2/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Что такое ИИ?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unconstrained.content").value("Длинный текст"))
                .andExpect(jsonPath("$.constrained.content").value("1. Коротко."))
                .andExpect(jsonPath("$.constrained.maxTokens").value(80))
                .andExpect(jsonPath("$.constrained.stop[0]").value("<<<END>>>"));
    }

    @Test
    void postCompareRejectsBlankPrompt() throws Exception {
        when(compareService.compare("   ")).thenThrow(new IllegalArgumentException("prompt must not be blank"));

        mockMvc.perform(post("/api/day2/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("prompt must not be blank"));
    }

    @Test
    void postCompareMapsLlmFailure() throws Exception {
        when(compareService.compare(any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day2/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }
}
