package com.yunovan.aiadvent.day16;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day16CliRunnerTest {

    private Day16CliRunner runner(Day16McpService service) {
        return new Day16CliRunner(service, new Day16Properties(8090, "/mcp", "ai-advent-mcp", "0.1.0"),
                mock(ApplicationContext.class));
    }

    private String captured(Day16McpService service, String... args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner(service).run(new DefaultApplicationArguments(args));
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }

    @Test
    void doesNothingWithoutDayFlag() {
        Day16McpService service = mock(Day16McpService.class);

        runner(service).run(new DefaultApplicationArguments());

        verifyNoInteractions(service);
    }

    @Test
    void printsConnectionDetailsWithCheck() {
        Day16McpService service = mock(Day16McpService.class);
        when(service.health()).thenReturn(
                new Day16HealthResponse(true, "2024-11-05", "ai-advent-mcp", "0.1.0", "s1", 2));

        String output = captured(service, "--day=16", "--check");

        assertThat(output).contains("СОЕДИНЕНИЕ С MCP");
        assertThat(output).contains("2024-11-05");
        assertThat(output).contains("ai-advent-mcp 0.1.0");
        assertThat(output).contains("tools: 2");
    }

    @Test
    void printsToolsWithToolsFlag() {
        Day16McpService service = mock(Day16McpService.class);
        when(service.tools()).thenReturn(java.util.List.of(
                new Day16ToolInfo("day16_sum", "Складывает два целых числа."),
                new Day16ToolInfo("day16_upper", "Текст в верхний регистр.")));

        String output = captured(service, "--day=16", "--tools");

        assertThat(output).contains("ИНСТРУМЕНТЫ MCP");
        assertThat(output).contains("day16_sum");
        assertThat(output).contains("day16_upper");
    }

    @Test
    void callsToolWithCallFlagAndParsesArguments() {
        Day16McpService service = mock(Day16McpService.class);
        when(service.call("day16_sum", Map.of("a", 6, "b", 7)))
                .thenReturn(new Day16CallResponse("day16_sum", "13", false));

        String output = captured(service, "--day=16", "--call=day16_sum", "--arg=a=6", "--arg=b=7");

        assertThat(output).contains("ВЫЗОВ ИНСТРУМЕНТА day16_sum");
        assertThat(output).contains("13");
        verify(service).call("day16_sum", Map.of("a", 6, "b", 7));
    }

    @Test
    void printsUsageWithoutCommands() {
        Day16McpService service = mock(Day16McpService.class);

        String output = captured(service, "--day=16");

        assertThat(output).contains("--check");
        assertThat(output).contains("--tools");
        verifyNoInteractions(service);
    }

    @Test
    void printsErrorWhenMcpUnavailable() {
        Day16McpService service = mock(Day16McpService.class);
        when(service.health()).thenThrow(new Day16McpException("MCP недоступен: Connection refused"));

        String output = captured(service, "--day=16", "--check");

        assertThat(output).contains("ОШИБКА: MCP недоступен: Connection refused");
    }
}