package com.yunovan.aiadvent.day17;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day17CliRunnerTest {

    private final ApplicationContext context = mock(ApplicationContext.class);

    private String run(Day17AgentService service, String... args) {
        Day17CliRunner runner = new Day17CliRunner(
                service, new Day17Properties(9090, "/mcp", "ai-advent-tracker-mcp", "0.1.0", "dir"), context);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.run(new DefaultApplicationArguments(args));
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }

    @Test
    void checkPrintsConnectionInfo() {
        Day17AgentService service = mock(Day17AgentService.class);
        when(service.health()).thenReturn(new Day17HealthResponse(true, "ai-advent-tracker-mcp", "0.1.0", 3));

        String output = run(service, "--day=17", "--check");

        assertThat(output).contains("=== СОЕДИНЕНИЕ С MCP ===");
        assertThat(output).contains("connected: true");
        assertThat(output).contains("ai-advent-tracker-mcp");
        assertThat(output).contains("tools: 3");
    }

    @Test
    void toolsPrintsToolList() {
        Day17AgentService service = mock(Day17AgentService.class);
        when(service.tools()).thenReturn(List.of(
                new Day17ToolInfo("tracker_create_task", "Создаёт задачу."),
                new Day17ToolInfo("tracker_list_tasks", "Список задач."),
                new Day17ToolInfo("tracker_add_comment", "Добавляет комментарий.")));

        String output = run(service, "--day=17", "--tools");

        assertThat(output).contains("=== ИНСТРУМЕНТЫ MCP ===");
        assertThat(output).contains("tracker_create_task");
        assertThat(output).contains("tracker_add_comment");
    }

    @Test
    void promptSubmitsAgentAndPrintsAnswer() {
        Day17AgentService service = mock(Day17AgentService.class);
        when(service.submit("создай задачу Привезти стол"))
                .thenReturn(new Day17AgentResponse("создай задачу Привезти стол",
                        "tracker_create_task", Map.of("title", "Привезти стол"),
                        "{\"id\":\"t-aaa\"}", false, "Задача создана!"));

        String output = run(service, "--day=17", "--prompt=создай задачу Привезти стол");

        assertThat(output).contains("tool: tracker_create_task");
        assertThat(output).contains("Задача создана!");
        assertThat(output).contains("=== АГЕНТ / MCP ===");
        verify(service).submit("создай задачу Привезти стол");
    }

    @Test
    void mcpFailurePrintsError() {
        Day17AgentService service = mock(Day17AgentService.class);
        when(service.health()).thenThrow(new Day17McpException("MCP недоступен"));

        String output = run(service, "--day=17", "--check");

        assertThat(output).contains("ОШИБКА: MCP недоступен");
    }

    @Test
    void noKnownFlagsPrintsUsage() {
        Day17AgentService service = mock(Day17AgentService.class);

        String output = run(service, "--day=17");

        assertThat(output).contains("--check");
        assertThat(output).contains("--prompt");
    }

    @Test
    void suppressedWhenDayIsNotSeventeen() {
        Day17AgentService service = mock(Day17AgentService.class);

        String output = run(service, "--day=16", "--check", "--tools");

        assertThat(output).doesNotContain("СОЕДИНЕНИЕ С MCP");
        assertThat(output).doesNotContain("ИНСТРУМЕНТЫ MCP");
    }
}