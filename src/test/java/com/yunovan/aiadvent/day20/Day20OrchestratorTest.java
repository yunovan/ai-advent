package com.yunovan.aiadvent.day20;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day18.Day18Job;
import com.yunovan.aiadvent.day18.Day18McpClient;
import com.yunovan.aiadvent.day18.Day18McpServer;
import com.yunovan.aiadvent.day18.Day18Properties;
import com.yunovan.aiadvent.day18.Day18SchedulerApi;
import com.yunovan.aiadvent.day18.Day18Sample;
import com.yunovan.aiadvent.day18.Day18Summary;
import com.yunovan.aiadvent.day19.Day19CatalogService;
import com.yunovan.aiadvent.day19.Day19MarketService;
import com.yunovan.aiadvent.day19.Day19McpClient;
import com.yunovan.aiadvent.day19.Day19McpServer;
import com.yunovan.aiadvent.day19.Day19Properties;
import com.yunovan.aiadvent.day19.Day19SaveService;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day20OrchestratorTest {

    @TempDir
    Path tempDir;

    private Day18McpServer schedulerServer;
    private Day19McpServer marketServer;
    private Day19MarketService market;
    private Day20Orchestrator orchestrator;

    @BeforeEach
    void setUp() {
        Day18SchedulerApi schedulerApi = mock(Day18SchedulerApi.class);
        Day18Job job = new Day18Job("j-100", "reminder", "Напоминание", "events", "waiting",
                1, null, null, null, "2026-01-01T10:00", "2026-01-01T10:01", null);
        when(schedulerApi.addReminder(any(), any())).thenReturn(job);
        when(schedulerApi.listJobs()).thenReturn(List.of(job));
        when(schedulerApi.summary(any(), any())).thenReturn(new Day18Summary(
                "events", "last 60s", 12, "10:00:01", "10:01:00", 99.8, 90.0, 100.0,
                1.0, "OK", List.of(new Day18Sample("s-1", "j-200", "events", "metric",
                "10:00:30", 99.8, "OK"))));
        when(schedulerApi.addCollector(any(), any(), any(), any())).thenReturn(
                new Day18Job("j-200", "collector", "Сбор", "events", "running",
                        2, 2, "https://demo.local/events", null, "2026-01-01T10:00", null, "2026-01-01T10:02"));

        schedulerServer = new Day18McpServer(
                new Day18Properties(0, "/mcp", "ai-advent-scheduler-mcp", "0.1.0", "dir", 50L),
                schedulerApi);
        schedulerServer.start();

        Day19SaveService saveService = new Day19SaveService(
                new Day19Properties(0, "/mcp", "ai-advent-pipeline-mcp", "0.1.0", tempDir.toString()));
        market = new Day19MarketService(new Day19CatalogService(), saveService);
        marketServer = new Day19McpServer(
                new Day19Properties(0, "/mcp", "ai-advent-pipeline-mcp", "0.1.0", "dir"), market);
        marketServer.start();

        Day18McpClient schedulerClient = new Day18McpClient(
                new Day18Properties(schedulerServer.boundPort(), "/mcp", "ai-advent-scheduler-mcp",
                        "0.1.0", "dir", 50L));
        Day19McpClient marketClient = new Day19McpClient(
                new Day19Properties(marketServer.boundPort(), "/mcp", "ai-advent-pipeline-mcp",
                        "0.1.0", "dir"));
        orchestrator = new Day20Orchestrator(schedulerClient, marketClient,
                new Day18Properties(schedulerServer.boundPort(), "/mcp", "ai-advent-scheduler-mcp",
                        "0.1.0", "dir", 50L),
                new Day19Properties(marketServer.boundPort(), "/mcp", "ai-advent-pipeline-mcp",
                        "0.1.0", "dir"));
    }

    @AfterEach
    void tearDown() {
        schedulerServer.stop();
        marketServer.stop();
    }

    @Test
    void healthReportsBothRegisteredServers() {
        Day20HealthResponse health = orchestrator.health();

        assertThat(health.connected()).isTrue();
        assertThat(health.servers()).hasSize(2);
        Day20ServerInfo scheduler = health.servers().get(0);
        assertThat(scheduler.server()).isEqualTo("scheduler");
        assertThat(scheduler.toolCount()).isEqualTo(6);
        assertThat(scheduler.connected()).isTrue();
        Day20ServerInfo marketServerInfo = health.servers().get(1);
        assertThat(marketServerInfo.server()).isEqualTo("market");
        assertThat(marketServerInfo.toolCount()).isEqualTo(3);
        assertThat(health.toolCount()).isEqualTo(9);
    }

    @Test
    void toolsAggregatesBothServersWithOwner() {
        List<Day20ToolEntry> tools = orchestrator.tools();

        assertThat(tools).hasSize(9);
        assertThat(tools.stream().filter(t -> t.server().equals("scheduler")))
                .extracting(Day20ToolEntry::name)
                .contains("scheduler_summary", "scheduler_add_reminder");
        assertThat(tools.stream().filter(t -> t.server().equals("market")))
                .extracting(Day20ToolEntry::name)
                .contains("search", "summarize", "saveToFile");
    }

    @Test
    void routeDispatchesMarketToolToMarketServer() {
        Day20CallResponse response = orchestrator.route("search", Map.of("query", "ноутбук"));

        assertThat(response.server()).isEqualTo("market");
        assertThat(response.tool()).isEqualTo("search");
        assertThat(response.success()).isTrue();
        assertThat(response.result()).contains("\"products\"");
        assertThat(response.result()).contains("Lenovo");
    }

    @Test
    void routeDispatchesSchedulerToolToSchedulerServer() {
        Day20CallResponse response = orchestrator.route("scheduler_list_jobs", Map.of());

        assertThat(response.server()).isEqualTo("scheduler");
        assertThat(response.tool()).isEqualTo("scheduler_list_jobs");
        assertThat(response.result()).contains("j-100");
    }

    @Test
    void routeUnknownToolThrowsWithHint() {
        assertThatThrownBy(() -> orchestrator.route("calculator", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не зарегистрирован");
    }

    @Test
    void callWithUnknownServerThrows() {
        assertThatThrownBy(() -> orchestrator.call("database", "search", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Неизвестный сервер");
    }

    @Test
    void runFlowMarketReportUsesToolsOfBothServersInOrder() {
        Day20FlowResponse response = orchestrator.runFlow("market-report",
                Map.of("query", "ноутбук", "format", "markdown", "fileName", "итог-2026"));

        assertThat(response.flow()).isEqualTo("market-report");
        assertThat(response.steps()).hasSize(4);
        assertThat(response.steps().get(0).server()).isEqualTo("market");
        assertThat(response.steps().get(0).tool()).isEqualTo("search");
        assertThat(response.steps().get(1).server()).isEqualTo("market");
        assertThat(response.steps().get(1).tool()).isEqualTo("summarize");
        assertThat(response.steps().get(2).server()).isEqualTo("market");
        assertThat(response.steps().get(2).tool()).isEqualTo("saveToFile");
        assertThat(response.steps().get(3).server()).isEqualTo("scheduler");
        assertThat(response.steps().get(3).tool()).isEqualTo("scheduler_add_reminder");
        assertThat(response.steps()).allMatch(Day20FlowStepResult::success);
    }

    @Test
    void runFlowMarketReportSubstitutesPreviousStepResults() {
        Day20FlowResponse response = orchestrator.runFlow("market-report",
                Map.of("query", "ноутбук", "format", "markdown", "fileName", "итог-2026"));

        String step4Arguments = String.valueOf(response.steps().get(3).arguments());
        assertThat(step4Arguments).contains("Отчёт по запросу «ноутбук»");
        assertThat(step4Arguments).contains("итог-2026");

        assertThat(String.valueOf(response.steps().get(1).arguments())).contains("\"products\"");
        assertThat(String.valueOf(response.steps().get(2).arguments()))
                .contains("\"products\"").contains("Сравнение по запросу");
        assertThat(Path.of(tempDir.toString(), "итог-2026.md")).exists();
    }

    @Test
    void runFlowSchedulerBriefUsesSchedulerOnly() {
        Day20FlowResponse response = orchestrator.runFlow("scheduler-brief",
                Map.of("feed", "events", "periodSeconds", 2, "url", "https://demo.local/events",
                        "sinceSeconds", 10));

        assertThat(response.steps()).hasSize(2);
        assertThat(response.steps().get(0).server()).isEqualTo("scheduler");
        assertThat(response.steps().get(0).tool()).isEqualTo("scheduler_add_collector");
        assertThat(response.steps().get(1).server()).isEqualTo("scheduler");
        assertThat(response.steps().get(1).tool()).isEqualTo("scheduler_summary");
        assertThat(response.steps()).allMatch(Day20FlowStepResult::success);
        assertThat(response.summary()).contains("успешно: 2/2");
    }

    @Test
    void runFlowUnknownThrows() {
        assertThatThrownBy(() -> orchestrator.runFlow("nightly-cleanup", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
    }
}