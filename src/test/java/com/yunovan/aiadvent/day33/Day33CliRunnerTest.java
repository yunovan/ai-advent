package com.yunovan.aiadvent.day33;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day33CliRunnerTest {

    private final Day33SupportService service = mock(Day33SupportService.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day33CliRunner runner = new Day33CliRunner(service, context);

    private String run(String... args) {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {
            runner.run(new DefaultApplicationArguments(args));
        } finally {
            System.setOut(original);
        }
        return buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Day33HealthResponse health() {
        return new Day33HealthResponse("ok", "http://localhost:9096/mcp", "data/day33-kb",
                "data/day33-crm", List.of("authorization.md", "faq.md"), 30, 9_000, 4, 5,
                new Day33McpStatus(true, "http://localhost:9096/mcp", 5, ""),
                new Day33ModelStatus("http://localhost:11434", "qwen2.5:3b", true, "0.35.1", true, ""));
    }

    private static Day33SupportResponse answer() {
        Day21Chunk chunk = new Day21Chunk("fixed", "authorization.md", "authorization.md",
                "Авторизация", "Вход", "authorization.md#fixed#0001", 0, 60, "текст");
        return new Day33SupportResponse("Почему не работает авторизация?", "T-1001", "U-1001",
                "Иван Петров", "Не работает авторизация", "Проверьте 2FA. Источник: authorization.md.",
                false, "qwen2.5:3b", "контекст клиента", "контекст тикета", true, 5, 1,
                List.of(new Day21SearchHit(chunk, 0.9, "snippet")), List.of("authorization.md"),
                85.0, 900L, 120, 40, 9.5, "");
    }

    @Test
    void ignoresOtherDays() {
        String output = run("--day=32");

        assertThat(output).isEmpty();
        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsKnowledgeBaseCrmAndMcp() {
        when(service.health()).thenReturn(health());

        String output = run("--day=33", "--check");

        assertThat(output).contains("Ассистент поддержки");
        assertThat(output).contains("authorization.md");
        assertThat(output).contains("инструментов 5");
        assertThat(output).contains("пользователей 4");
        assertThat(output).contains("обращений 5");
    }

    @Test
    void kbPrintsDocuments() {
        when(service.kb()).thenReturn(List.of(
                new Day33DocumentInfo("faq.md", "faq.md", "FAQ", "markdown", 1200, 3)));

        String output = run("--day=33", "--kb");

        assertThat(output).contains("База знаний");
        assertThat(output).contains("faq.md");
    }

    @Test
    void usersPrintsCrmUsers() {
        when(service.users()).thenReturn(List.of(
                new Day33UserInfo("U-1001", "Иван Петров", "ivan@example.com", "Business", "X", "2023", 2)));

        String output = run("--day=33", "--users");

        assertThat(output).contains("Пользователи CRM");
        assertThat(output).contains("Иван Петров");
    }

    @Test
    void ticketsPrintsCrmTickets() {
        when(service.tickets()).thenReturn(List.of(
                new Day33TicketInfo("T-1001", "U-1001", "Не работает авторизация", "open", "high",
                        "Web", "2026-10-13T18:40:00", List.of("auth"))));

        String output = run("--day=33", "--tickets");

        assertThat(output).contains("Обращения CRM");
        assertThat(output).contains("T-1001");
    }

    @Test
    void toolsPrintsMcpCatalog() {
        when(service.tools()).thenReturn(new Day33ToolsResponse(true, "http://localhost:9096/mcp",
                1, List.of(new Day33ToolInfo("crm_ticket", "обращение")), ""));

        String output = run("--day=33", "--tools");

        assertThat(output).contains("MCP-инструменты CRM");
        assertThat(output).contains("crm_ticket");
    }

    @Test
    void crmPrintsToolOutput() {
        when(service.crm(any(), any()))
                .thenReturn(new Day33CrmResponse("crm_ticket", true, "Тикет T-1001"));

        String output = run("--day=33", "--crm=ticket", "--id=T-1001");

        assertThat(output).contains("Инструмент MCP: crm_ticket");
        assertThat(output).contains("Тикет T-1001");
    }

    @Test
    void askPrintsAnswerAndMetrics() {
        when(service.ask(any(), any(), any())).thenReturn(answer());

        String output = run("--day=33", "--ask=Почему не работает авторизация?", "--ticket=T-1001");

        assertThat(output).contains("Ответ ассистента поддержки");
        assertThat(output).contains("Проверьте 2FA. Источник: authorization.md.");
        assertThat(output).contains("900 мс");
        assertThat(output).contains("опора на источники 85%");
    }

    @Test
    void usageWhenNoAction() {
        String output = run("--day=33");

        assertThat(output).contains("--check");
        assertThat(output).contains("--kb");
        assertThat(output).contains("--ask");
        verifyNoInteractions(service);
    }

    @Test
    void printsErrorWhenCrmUnavailable() {
        when(service.crm(any(), any())).thenThrow(new Day33SupportException("MCP недоступен"));

        String output = run("--day=33", "--crm=ticket", "--id=T-1001");

        assertThat(output).contains("Ошибка:");
        assertThat(output).contains("MCP недоступен");
    }
}
