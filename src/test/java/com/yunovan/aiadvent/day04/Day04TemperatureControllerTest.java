package com.yunovan.aiadvent.day04;

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

@WebMvcTest(controllers = {Day04TemperatureController.class, ApiExceptionHandler.class})
class Day04TemperatureControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day04TemperatureService temperatureService;

    @Test
    void postCompareReturnsThreeTemperatures() throws Exception {
        when(temperatureService.compare("Париж"))
                .thenReturn(new TemperatureResponse(
                        "Париж",
                        "openai/gpt-4o-mini",
                        List.of(
                                new TemperatureSample(0.0, "точно", "stop", 1, "факты"),
                                new TemperatureSample(0.7, "баланс", "stop", 1, "чат"),
                                new TemperatureSample(1.2, "ярко", "stop", 1, "идеи")),
                        Day04Temperatures.CONCLUSIONS));

        mockMvc.perform(post("/api/day4/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Париж\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.samples[0].temperature").value(0.0))
                .andExpect(jsonPath("$.samples[1].temperature").value(0.7))
                .andExpect(jsonPath("$.samples[2].temperature").value(1.2))
                .andExpect(jsonPath("$.conclusions").exists());
    }

    @Test
    void postCompareRejectsBlankPrompt() throws Exception {
        when(temperatureService.compare("   ")).thenThrow(new IllegalArgumentException("prompt must not be blank"));

        mockMvc.perform(post("/api/day4/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("prompt must not be blank"));
    }

    @Test
    void postCompareMapsLlmFailure() throws Exception {
        when(temperatureService.compare(any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day4/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }
}
