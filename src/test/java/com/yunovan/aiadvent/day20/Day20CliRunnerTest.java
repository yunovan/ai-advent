package com.yunovan.aiadvent.day20;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day20CliRunnerTest {

    private final ApplicationContext context = mock(ApplicationContext.class);

    private String run(Day20Orchestrator orchestrator, Day20AgentService service, String... args) {
        Day20CliRunner runner = new Day20CliRunner(orchestrator, service,
                new Day20Properties(9093, "/mcp", "ai-advent-orchestrator-mcp", "0.1.0"), context);
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
        Day20Orchestrator orchestrator = mock(Day20Orchestrator.class);
        when(orchestrator.health()).thenReturn(new Day20HealthResponse(true, List.of(
                new Day20ServerInfo("scheduler", "ai-advent-scheduler-mcp", "0.1.0", 6, true),
                new Day20ServerInfo("market", "ai-advent-pipeline-mcp", "0.1.0", 3, true)), 9));

        String output = run(orchestrator, mock(Day20AgentService.class), "--day=20", "--check");

        assertThat(output).contains("=== ОРКЕСТРАТОР MCP ===");
        assertThat(output).contains("соединение: ОК");
        assertThat(output).contains("scheduler");
        assertThat(output).contains("market");
        assertThat(output).contains("всего инструментов: 9");
    }

    @Test
    void toolsPrintsAggregatedToolsWithServer() {
        Day20Orchestrator orchestrator = mock(Day20Orchestrator.class);
        when(orchestrator.tools()).thenReturn(List.of(
                new Day20ToolEntry("scheduler", "scheduler_summary", "Сводка."),
                new Day20ToolEntry("market", "search", "Поиск.")));

        String output = run(orchestrator, mock(Day20AgentService.class), "--day=20", "--tools");

        assertThat(output).contains("=== ИНСТРУМЕНТЫ ВСЕХ MCP-СЕРВЕРОВ ===");
        assertThat(output).contains("[scheduler] scheduler_summary");
        assertThat(output).contains("[market] search");
    }

    @Test
    void routePrintsResultAndServer() {
        Day20Orchestrator orchestrator = mock(Day20Orchestrator.class);
        when(orchestrator.route(eq("search"), any())).thenReturn(
                new Day20CallResponse("market", "search", Map.of("query", "ноутбук"), true,
                        "{\"products\":[]}"));

        String output = run(orchestrator, mock(Day20AgentService.class),
                "--day=20", "--route=search", "--json={\"query\":\"ноутбук\"}");

        assertThat(output).contains("=== МАРШРУТИЗАЦИЯ: search ===");
        assertThat(output).contains("сервер: market");
        assertThat(output).contains("{\"products\":[]}");
    }

    @Test
    void flowPrintsStepsInOrder() {
        Day20Orchestrator orchestrator = mock(Day20Orchestrator.class);
        when(orchestrator.runFlow(eq("market-report"), any())).thenReturn(new Day20FlowResponse(
                "market-report", "Кросс-серверный флоу",
                Map.of("query", "ноутбук", "format", "markdown"), List.of(
                        new Day20FlowStepResult("market", "search", Map.of("query", "ноутбук"),
                                true, "{\"products\":[]}"),
                        new Day20FlowStepResult("scheduler", "scheduler_add_reminder",
                                Map.of("topic", "Готово"), true, "{}")),
                "Флоу «market-report» — шагов: 2, успешно: 2/2"));

        String output = run(orchestrator, mock(Day20AgentService.class),
                "--day=20", "--flow=market-report", "--json={\"query\":\"ноутбук\",\"format\":\"markdown\"}");

        assertThat(output).contains("=== ФЛОУ: market-report ===");
        assertThat(output).contains("1. [market] search — OK");
        assertThat(output).contains("2. [scheduler] scheduler_add_reminder — OK");
        assertThat(output).contains("итог: Флоу «market-report» — шагов: 2, успешно: 2/2");
    }

    @Test
    void promptRunsAgent() {
        Day20Orchestrator orchestrator = mock(Day20Orchestrator.class);
        Day20AgentService service = mock(Day20AgentService.class);
        when(service.submit(eq("найди ноутбуки"))).thenReturn(new Day20AgentResponse(
                "найди ноутбуки", "market", "market", "search", Map.of("query", "ноутбуки"),
                "{\"products\":[]}", "Нашёл товары!"));

        String output = run(orchestrator, service, "--day=20", "--prompt=найди ноутбуки");

        assertThat(output).contains("=== АГЕНТ / ОРКЕСТРАТОР ===");
        assertThat(output).contains("intent: market");
        assertThat(output).contains("server: market");
        assertThat(output).contains("Нашёл товары!");
    }

    @Test
    void badJsonPrintsError() {
        Day20Orchestrator orchestrator = mock(Day20Orchestrator.class);

        String output = run(orchestrator, mock(Day20AgentService.class),
                "--day=20", "--route=search", "--json={broken");

        assertThat(output).contains("ОШИБКА: Не удалось разобрать --json");
    }

    @Test
    void whenNotDayTwentyRunsNothing() {
        Day20Orchestrator orchestrator = mock(Day20Orchestrator.class);
        Day20AgentService service = mock(Day20AgentService.class);

        String output = run(orchestrator, service, "--day=1", "--prompt=привет");

        assertThat(output).contains("Day 20 web UI");
        assertThat(output).doesNotContain("=== АГЕНТ / ОРКЕСТРАТОР ===");
    }
}