package com.yunovan.aiadvent.day07;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.DialogNotFoundException;
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

@WebMvcTest(controllers = {Day07DialogController.class, ApiExceptionHandler.class})
class Day07DialogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day07DialogService service;

    @Test
    void postDialogsStartsDialog() throws Exception {
        when(service.start()).thenReturn(new Day07StartResponse("d1", Instant.now(), List.of(), List.of()));

        mockMvc.perform(post("/api/day7/dialogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogId").value("d1"));
    }

    @Test
    void getDialogsListsFinished() throws Exception {
        when(service.dialogs()).thenReturn(List.of(new Day07DialogSummary(
                "d1", Instant.now(), Instant.now(), "итог", 2)));

        mockMvc.perform(get("/api/day7/dialogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].summary").value("итог"));
    }

    @Test
    void postChatReturnsReplyWithHistory() throws Exception {
        when(service.chat("d1", "Привет меня зовут Ася")).thenReturn(new Day07ChatResponse(
                "d1",
                "Привет меня зовут Ася",
                "Приятно познакомиться, Ася!",
                "gpt-4o-mini",
                2,
                500L,
                List.of(
                        ConversationMessage.user("Привет меня зовут Ася"),
                        ConversationMessage.assistant("Приятно познакомиться, Ася!")),
                List.of()));

        mockMvc.perform(post("/api/day7/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Привет меня зовут Ася\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dialogId").value("d1"))
                .andExpect(jsonPath("$.request").value("Привет меня зовут Ася"))
                .andExpect(jsonPath("$.content").value("Приятно познакомиться, Ася!"))
                .andExpect(jsonPath("$.messageCount").value(2))
                .andExpect(jsonPath("$.history[1].content").value("Приятно познакомиться, Ася!"))
                .andExpect(jsonPath("$.elapsedMs").value(500));
    }

    @Test
    void postChatRejectsBlankRequest() throws Exception {
        when(service.chat(any(), any()))
                .thenThrow(new IllegalArgumentException("user request must not be blank"));

        mockMvc.perform(post("/api/day7/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("user request must not be blank"));
    }

    @Test
    void postChatMapsLlmFailure() throws Exception {
        when(service.chat(any(), any())).thenThrow(new LlmException("LLM API error 401: bad key"));

        mockMvc.perform(post("/api/day7/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("LLM API error 401: bad key"));
    }

    @Test
    void postChatMapsUnknownDialogToNotFound() throws Exception {
        when(service.chat(any(), any())).thenThrow(new DialogNotFoundException("d1"));

        mockMvc.perform(post("/api/day7/dialogs/d1/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"request\":\"Hello\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Диалог 'd1' не найден"));
    }

    @Test
    void getChatUsesRequestParam() throws Exception {
        when(service.chat("d1", "Как дела?")).thenReturn(new Day07ChatResponse(
                "d1", "Как дела?", "Отлично", "gpt-4o-mini", 2, 10L,
                List.of(), List.of()));

        mockMvc.perform(get("/api/day7/dialogs/d1/chat").param("request", "Как дела?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Отлично"));
    }

    @Test
    void postFinishReturnsSummary() throws Exception {
        when(service.finish("d1")).thenReturn(new Day07FinishResponse("d1", Instant.now(), "Итог диалога", 2));

        mockMvc.perform(post("/api/day7/dialogs/d1/finish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Итог диалога"));
    }
}