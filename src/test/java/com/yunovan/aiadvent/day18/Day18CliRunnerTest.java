package com.yunovan.aiadvent.day18;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

class Day18CliRunnerTest {

    private final ApplicationContext context = mock(ApplicationContext.class);

    private String run(Day18SchedulerApi scheduler, Day18AgentService service, String... args) {
        Day18CliRunner runner = new Day18CliRunner(scheduler, service,
                new Day18Properties(9091, "/mcp", "ai-advent-scheduler-mcp", "0.1.0", "dir", 500L), context);
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

    private Day18Job job(String id, String type, String status) {
        return new Day18Job(id, type, "events", "events", status, null, 5, null, null,
                "2026-01-01T00:00:00Z", null, "2026-01-01T00:00:05Z");
    }

    @Test
    void checkPrintsConnectionInfo() {
        Day18AgentService service = mock(Day18AgentService.class);
        when(service.health()).thenReturn(new Day18HealthResponse(true, "ai-advent-scheduler-mcp", "0.1.0", 6));

        String output = run(mock(Day18SchedulerApi.class), service, "--day=18", "--check");

        assertThat(output).contains("=== СОЕДИНЕНИЕ С MCP ===");
        assertThat(output).contains("connected: true");
        assertThat(output).contains("ai-advent-scheduler-mcp");
        assertThat(output).contains("tools: 6");
    }

    @Test
    void toolsPrintsToolList() {
        Day18AgentService service = mock(Day18AgentService.class);
        when(service.tools()).thenReturn(List.of(
                new Day18ToolInfo("scheduler_add_reminder", "Напоминание."),
                new Day18ToolInfo("scheduler_add_collector", "Периодический сбор."),
                new Day18ToolInfo("scheduler_list_jobs", "Список заданий."),
                new Day18ToolInfo("scheduler_summary", "Сводка."),
                new Day18ToolInfo("scheduler_run_now", "Выполнить сейчас."),
                new Day18ToolInfo("scheduler_stop_process", "Остановить процесс.")));

        String output = run(mock(Day18SchedulerApi.class), service, "--day=18", "--tools");

        assertThat(output).contains("=== ИНСТРУМЕНТЫ MCP ===");
        assertThat(output).contains("scheduler_add_reminder");
        assertThat(output).contains("scheduler_run_now");
        assertThat(output).contains("scheduler_stop_process");
    }

    @Test
    void jobsPrintsJobList() {
        Day18SchedulerApi scheduler = mock(Day18SchedulerApi.class);
        when(scheduler.listJobs()).thenReturn(List.of(job("j-aaa", "collector", "active")));

        String output = run(scheduler, mock(Day18AgentService.class), "--day=18", "--jobs");

        assertThat(output).contains("заданий: 1");
        assertThat(output).contains("j-aaa");
        assertThat(output).contains("active");
    }

    @Test
    void reminderCallsSchedulerWithDelay() {
        Day18SchedulerApi scheduler = mock(Day18SchedulerApi.class);
        when(scheduler.addReminder("выпить чай", 10)).thenReturn(job("j-r1", "reminder", "pending"));

        String output = run(scheduler, mock(Day18AgentService.class),
                "--day=18", "--reminder=выпить чай", "--delay=10");

        assertThat(output).contains("=== НАПОМИНАНИЕ ===");
        assertThat(output).contains("j-r1");
        assertThat(output).contains("reminder");
        verify(scheduler).addReminder("выпить чай", 10);
    }

    @Test
    void collectCallsSchedulerWithDefaultFeedAndPeriod() {
        Day18SchedulerApi scheduler = mock(Day18SchedulerApi.class);
        when(scheduler.addCollector(anyString(), anyInt(), isNull(), isNull()))
                .thenReturn(job("j-c1", "collector", "active"));

        String output = run(scheduler, mock(Day18AgentService.class),
                "--day=18", "--collect", "--feed=events", "--period=3");

        assertThat(output).contains("=== ПЕРИОДИЧЕСКИЙ СБОР ===");
        assertThat(output).contains("j-c1");
        verify(scheduler).addCollector(eq("events"), eq(3), isNull(), isNull());
    }

    @Test
    void stopStopsAllProcessesWhenNoIdGiven() {
        Day18SchedulerApi scheduler = mock(Day18SchedulerApi.class);
        when(scheduler.stopProcess(isNull())).thenReturn(List.of(
                job("j-1", "collector", "stopped"), job("j-2", "collector", "stopped")));

        String output = run(scheduler, mock(Day18AgentService.class), "--day=18", "--stop");

        assertThat(output).contains("=== ОСТАНОВЛЕНО ПРОЦЕССОВ: 2 ===");
        assertThat(output).contains("j-1");
        assertThat(output).contains("stopped");
        verify(scheduler).stopProcess(isNull());
    }

    @Test
    void summaryPrintsAggregation() {
        Day18SchedulerApi scheduler = mock(Day18SchedulerApi.class);
        when(scheduler.summary(isNull(), anyInt())).thenReturn(new Day18Summary(
                null, null, 2, "2026-01-01T00:00:01Z", "2026-01-01T00:00:02Z",
                1.5, 1.0, 2.0, null, "мок-замер №2", List.of()));

        String output = run(scheduler, mock(Day18AgentService.class), "--day=18", "--summary");

        assertThat(output).contains("=== СВОДКА ===");
        assertThat(output).contains("событий: 2");
        assertThat(output).contains("среднее значение: 1.5");
    }

    @Test
    void promptSubmitsAgentAndPrintsAnswer() {
        Day18AgentService service = mock(Day18AgentService.class);
        when(service.submit("дай сводку по events"))
                .thenReturn(new Day18AgentResponse("дай сводку по events",
                        "scheduler_summary", Map.of("feed", "events"),
                        "{\"count\":2}", false, "Сводка готова!"));

        String output = run(mock(Day18SchedulerApi.class), service,
                "--day=18", "--prompt=дай сводку по events");

        assertThat(output).contains("=== АГЕНТ / MCP ===");
        assertThat(output).contains("tool: scheduler_summary");
        assertThat(output).contains("Сводка готова!");
        verify(service).submit("дай сводку по events");
    }

    @Test
    void noKnownFlagsPrintsUsage() {
        String output = run(mock(Day18SchedulerApi.class), mock(Day18AgentService.class), "--day=18");

        assertThat(output).contains("--reminder");
        assertThat(output).contains("--collect");
        assertThat(output).contains("--live");
    }

    @Test
    void suppressedWhenDayIsNotEighteen() {
        Day18AgentService service = mock(Day18AgentService.class);

        String output = run(mock(Day18SchedulerApi.class), service, "--day=17", "--check", "--tools");

        assertThat(output).doesNotContain("СОЕДИНЕНИЕ С MCP");
        assertThat(output).doesNotContain("ИНСТРУМЕНТЫ MCP");
    }
}