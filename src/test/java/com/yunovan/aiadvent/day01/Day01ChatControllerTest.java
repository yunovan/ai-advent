package com.yunovan.aiadvent.day01;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day01ChatController.class, ApiExceptionHandler.class})
@EnableConfigurationProperties(LlmProperties.class)
class Day01ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmClient llmClient;

    @Test
    void postChatReturnsModelReply() throws Exception {
        when(llmClient.complete("Привет")).thenReturn("Ответ модели");

        mockMvc.perform(post("/api/day1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Привет\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt").value("Привет"))
                .andExpect(jsonPath("$.model").value("openai/gpt-4o-mini"))
                .andExpect(jsonPath("$.content").value("Ответ модели"));
    }

    @Test
    void getChatReturnsModelReply() throws Exception {
        when(llmClient.complete("Hello")).thenReturn("Hi");

        mockMvc.perform(get("/api/day1/chat").param("prompt", "Hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hi"));
    }

    @Test
    void postChatRejectsBlankPrompt() throws Exception {
        mockMvc.perform(post("/api/day1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("prompt must not be blank"));

        verifyNoInteractions(llmClient);
    }

    @Test
    void postChatMapsLlmFailure() throws Exception {
        when(llmClient.complete("Hello")).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }
}
