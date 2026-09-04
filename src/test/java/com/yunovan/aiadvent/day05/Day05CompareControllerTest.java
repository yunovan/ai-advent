package com.yunovan.aiadvent.day05;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmReply;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day05CompareController.class, ApiExceptionHandler.class})
class Day05CompareControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day05CompareService compareService;

    @Test
    void postCompareReturnsThreeModelsAndMetrics() throws Exception {
        ModelRun weak = ModelRun.from(
                ModelTier.WEAK,
                Day5Models.WEAK_MODEL,
                new LlmReply("short", "stop", 10, 20, 30, new BigDecimal("0.00001"), 100L));
        ModelRun medium = ModelRun.from(
                ModelTier.MEDIUM,
                Day5Models.MEDIUM_MODEL,
                new LlmReply("mid", "stop", 10, 40, 50, new BigDecimal("0.00005"), 250L));
        ModelRun strong = ModelRun.from(
                ModelTier.STRONG,
                Day5Models.STRONG_MODEL,
                new LlmReply("long", "stop", 10, 80, 90, new BigDecimal("0.00020"), 800L));
        when(compareService.compare("Крыло"))
                .thenReturn(new ModelComparisonResponse(
                        "Крыло",
                        List.of(weak, medium, strong),
                        "слабая быстрее",
                        List.of(new ModelLink("Каталог OpenRouter", Day5Models.MODELS_CATALOG_OPENROUTER))));

        mockMvc.perform(post("/api/day5/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Крыло\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runs[0].tier").value("weak"))
                .andExpect(jsonPath("$.runs[1].tier").value("medium"))
                .andExpect(jsonPath("$.runs[2].tier").value("strong"))
                .andExpect(jsonPath("$.runs[0].elapsedMs").value(100))
                .andExpect(jsonPath("$.runs[2].totalTokens").value(90))
                .andExpect(jsonPath("$.runs[0].costLabel").value("$0.00001"))
                .andExpect(jsonPath("$.conclusion").exists())
                .andExpect(jsonPath("$.links[0].url").value(Day5Models.MODELS_CATALOG_OPENROUTER));
    }

    @Test
    void postCompareRejectsBlankPrompt() throws Exception {
        when(compareService.compare("   ")).thenThrow(new IllegalArgumentException("prompt must not be blank"));

        mockMvc.perform(post("/api/day5/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("prompt must not be blank"));
    }

    @Test
    void postCompareMapsLlmFailure() throws Exception {
        when(compareService.compare(any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day5/compare")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }
}
