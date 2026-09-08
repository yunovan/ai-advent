package com.yunovan.aiadvent.day06;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.agent.Agent;
import com.yunovan.aiadvent.agent.AgentReply;
import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.llm.LlmException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day06AgentController.class, ApiExceptionHandler.class})
class Day06AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Agent agent;

    @Test
    void postChatReturnsAgentReply() throws Exception {
        when(agent.ask("Кто ты?"))
                .thenReturn(new AgentReply("Я агент.", "gpt-4o-mini", 12, 20, 32, new BigDecimal("0.00001"), 640L));

        mockMvc.perform(post("/api/day6/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Кто ты?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request").value("Кто ты?"))
                .andExpect(jsonPath("$.content").value("Я агент."))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.totalTokens").value(32))
                .andExpect(jsonPath("$.costUsd").value(0.00001))
                .andExpect(jsonPath("$.elapsedMs").value(640));
    }

    @Test
    void getChatAcceptsRequestParam() throws Exception {
        when(agent.ask("Hello"))
                .thenReturn(new AgentReply("Hi!", "gpt-4o-mini", 5, 5, 10, null, 100L));

        mockMvc.perform(get("/api/day6/chat").param("request", "Hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Hi!"));
    }

    @Test
    void postChatRejectsBlankRequest() throws Exception {
        when(agent.ask(any())).thenThrow(new IllegalArgumentException("user request must not be blank"));

        mockMvc.perform(post("/api/day6/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("user request must not be blank"));
    }

    @Test
    void postChatMapsLlmFailure() throws Exception {
        when(agent.ask(any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day6/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }
}