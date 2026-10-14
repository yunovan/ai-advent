package com.yunovan.aiadvent.day33;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day33Controller.class, ApiExceptionHandler.class})
class Day33ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day33SupportService service;

    private static Day33HealthResponse health() {
        return new Day33HealthResponse("ok", "http://localhost:9096/mcp", "data/day33-kb",
                "data/day33-crm", List.of("authorization.md", "faq.md"), 30, 9_000, 4, 5,
                new Day33McpStatus(true, "http://localhost:9096/mcp", 5, ""),
                new Day33ModelStatus("http://localhost:11434", "qwen2.5:3b", true, "0.35.1", true, ""));
    }

    private static Day33SupportResponse answer() {
        Day21Chunk chunk = new Day21Chunk("fixed", "authorization.md", "authorization.md",
                "Авторизация", "Почему не работает вход", "authorization.md#fixed#0001", 0, 80,
                "Проверьте 2FA и синхронизацию времени.");
        return new Day33SupportResponse("Почему не работает авторизация?", "T-1001", "U-1001",
                "Иван Петров", "Не работает авторизация", "Проверьте 2FA. Источник: authorization.md.",
                false, "qwen2.5:3b", "контекст пользователя", "контекст обращения", true, 5, 1,
                List.of(new Day21SearchHit(chunk, 0.9, "snippet")), List.of("authorization.md"),
                85.0, 900L, 120, 40, 9.5, "");
    }

    @Test
    void healthReturnsKbCrmAndMcp() throws Exception {
        when(service.health()).thenReturn(health());

        mockMvc.perform(get("/api/day33/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.documents[0]").value("authorization.md"))
                .andExpect(jsonPath("$.users").value(4))
                .andExpect(jsonPath("$.tickets").value(5))
                .andExpect(jsonPath("$.mcp.available").value(true))
                .andExpect(jsonPath("$.mcp.tools").value(5))
                .andExpect(jsonPath("$.model.installed").value(true));
    }

    @Test
    void kbListsDocuments() throws Exception {
        when(service.kb()).thenReturn(List.of(
                new Day33DocumentInfo("faq.md", "faq.md", "FAQ", "markdown", 1200, 3)));

        mockMvc.perform(get("/api/day33/kb"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("faq.md"))
                .andExpect(jsonPath("$[0].chunks").value(3));
    }

    @Test
    void usersListsCrmUsers() throws Exception {
        when(service.users()).thenReturn(List.of(
                new Day33UserInfo("U-1001", "Иван Петров", "ivan@example.com", "Business",
                        "ООО «Ромашка»", "2023-04-12", 2)));

        mockMvc.perform(get("/api/day33/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("U-1001"))
                .andExpect(jsonPath("$[0].tickets").value(2));
    }

    @Test
    void ticketsListsCrmTickets() throws Exception {
        when(service.tickets()).thenReturn(List.of(
                new Day33TicketInfo("T-1001", "U-1001", "Не работает авторизация", "open", "high",
                        "Веб-приложение", "2026-10-13T18:40:00", List.of("auth", "2fa"))));

        mockMvc.perform(get("/api/day33/tickets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("T-1001"))
                .andExpect(jsonPath("$[0].priority").value("high"));
    }

    @Test
    void toolsExposeMcpCatalog() throws Exception {
        when(service.tools()).thenReturn(new Day33ToolsResponse(true,
                "http://localhost:9096/mcp", 1, List.of(new Day33ToolInfo("crm_ticket", "обращение")), ""));

        mockMvc.perform(get("/api/day33/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.tools[0].name").value("crm_ticket"));
    }

    @Test
    void crmCallsMcpTool() throws Exception {
        when(service.crm(any(), any())).thenReturn(new Day33CrmResponse("crm_ticket", true, "Тикет T-1001"));

        mockMvc.perform(post("/api/day33/crm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"crm_ticket\",\"arguments\":{\"ticketId\":\"T-1001\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool").value("crm_ticket"))
                .andExpect(jsonPath("$.content").value("Тикет T-1001"));
    }

    @Test
    void crmWithUnknownToolReturnsBadRequest() throws Exception {
        when(service.crm(any(), any()))
                .thenThrow(new IllegalArgumentException("Неизвестный инструмент CRM"));

        mockMvc.perform(post("/api/day33/crm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Неизвестный инструмент CRM"));
    }

    @Test
    void crmWhenMcpUnavailableReturnsBadGateway() throws Exception {
        when(service.crm(any(), any())).thenThrow(new Day33SupportException("MCP недоступен"));

        mockMvc.perform(post("/api/day33/crm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"crm_ticket\"}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void askReturnsAnswerWithContext() throws Exception {
        when(service.ask(any(), any(), any())).thenReturn(answer());

        mockMvc.perform(post("/api/day33/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Почему не работает авторизация?\","
                                + "\"ticketId\":\"T-1001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Проверьте 2FA. Источник: authorization.md."))
                .andExpect(jsonPath("$.ticketId").value("T-1001"))
                .andExpect(jsonPath("$.userId").value("U-1001"))
                .andExpect(jsonPath("$.mcpUsed").value(true))
                .andExpect(jsonPath("$.sourceFiles[0]").value("authorization.md"))
                .andExpect(jsonPath("$.groundingPercent").value(85.0))
                .andExpect(jsonPath("$.latencyMs").value(900));
    }

    @Test
    void askWithBlankQuestionReturnsBadRequest() throws Exception {
        when(service.ask(any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Вопрос не может быть пустым"));

        mockMvc.perform(post("/api/day33/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Вопрос не может быть пустым"));
    }
}
