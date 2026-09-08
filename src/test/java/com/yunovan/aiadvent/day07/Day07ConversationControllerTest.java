package com.yunovan.aiadvent.day07;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.ConversationReply;
import com.yunovan.aiadvent.agent.ConversationalAgent;
import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.llm.LlmException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day07ConversationController.class, ApiExceptionHandler.class})
class Day07ConversationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationalAgent agent;

    @Test
    void postChatReturnsReplyWithHistory() throws Exception {
        ConversationReply reply = new ConversationReply(
                "default",
                "Меня зовут Ася!",
                "gpt-4o-mini",
                3,
                10,
                7,
                17,
                new BigDecimal("0.00001"),
                500L,
                List.of(
                        ConversationMessage.system("sys"),
                        ConversationMessage.user("Привет, меня зовут Ася"),
                        ConversationMessage.assistant("Меня зовут Ася!")));
        when(agent.ask("default", "Привет, меня зовут Ася")).thenReturn(reply);

        mockMvc.perform(post("/api/day7/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"default\",\"request\":\"Привет, меня зовут Ася\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("default"))
                .andExpect(jsonPath("$.request").value("Привет, меня зовут Ася"))
                .andExpect(jsonPath("$.content").value("Меня зовут Ася!"))
                .andExpect(jsonPath("$.messageCount").value(3))
                .andExpect(jsonPath("$.history[1].content").value("Привет, меня зовут Ася"))
                .andExpect(jsonPath("$.elapsedMs").value(500));
    }

    @Test
    void getChatUsesDefaultSessionWhenNotProvided() throws Exception {
        when(agent.ask("default", "Как меня зовут?"))
                .thenReturn(new ConversationReply(
                        "default", "Ася", "gpt-4o-mini", 3, 1, 1, 2, null, 10L, List.of()));

        mockMvc.perform(get("/api/day7/chat").param("request", "Как меня зовут?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("default"));
    }

    @Test
    void postChatRejectsBlankRequest() throws Exception {
        when(agent.ask(any(), any())).thenThrow(new IllegalArgumentException("user request must not be blank"));

        mockMvc.perform(post("/api/day7/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"default\",\"request\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("user request must not be blank"));
    }

    @Test
    void postChatMapsLlmFailure() throws Exception {
        when(agent.ask(any(), any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day7/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"default\",\"request\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }

    @Test
    void postResetDeletesHistory() throws Exception {
        mockMvc.perform(post("/api/day7/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"default\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("default"))
                .andExpect(jsonPath("$.message").exists());

        verify(agent).reset("default");
    }
}