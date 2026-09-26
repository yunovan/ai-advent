package com.yunovan.aiadvent.day20;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunovan.aiadvent.day18.Day18Job;
import com.yunovan.aiadvent.day18.Day18McpClient;
import com.yunovan.aiadvent.day18.Day18McpServer;
import com.yunovan.aiadvent.day18.Day18Properties;
import com.yunovan.aiadvent.day18.Day18SchedulerApi;
import com.yunovan.aiadvent.day18.Day18Summary;
import com.yunovan.aiadvent.day19.Day19CatalogService;
import com.yunovan.aiadvent.day19.Day19MarketService;
import com.yunovan.aiadvent.day19.Day19McpClient;
import com.yunovan.aiadvent.day19.Day19McpServer;
import com.yunovan.aiadvent.day19.Day19Properties;
import com.yunovan.aiadvent.day19.Day19SaveService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day20McpServerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Day18McpServer schedulerServer;
    private Day19McpServer marketServer;
    private Day20McpServer server;
    private HttpClient http;
    private String url;

    @BeforeEach
    void setUp() {
        Day18SchedulerApi schedulerApi = mock(Day18SchedulerApi.class);
        Day18Job job = new Day18Job("j-100", "reminder", "Напоминание", "events", "waiting",
                1, null, null, null, "2026-01-01T10:00", "2026-01-01T10:01", null);
        when(schedulerApi.addReminder(any(), any())).thenReturn(job);
        when(schedulerApi.listJobs()).thenReturn(List.of(job));
        when(schedulerApi.summary(any(), any())).thenReturn(new Day18Summary(
                "events", "last 60s", 12, "10:00:01", "10:01:00", 99.8, 90.0, 100.0,
                1.0, "OK", List.of()));
        when(schedulerApi.addCollector(any(), any(), any(), any())).thenReturn(
                new Day18Job("j-200", "collector", "Сбор", "events", "running",
                        2, 2, "https://demo.local/events", null, "2026-01-01T10:00", null, "2026-01-01T10:02"));

        schedulerServer = new Day18McpServer(
                new Day18Properties(0, "/mcp", "ai-advent-scheduler-mcp", "0.1.0", "dir", 50L),
                schedulerApi);
        schedulerServer.start();

        Day19SaveService saveService = new Day19SaveService(
                new Day19Properties(0, "/mcp", "ai-advent-pipeline-mcp", "0.1.0", tempDir.toString()));
        Day19MarketService market = new Day19MarketService(new Day19CatalogService(), saveService);
        marketServer = new Day19McpServer(
                new Day19Properties(0, "/mcp", "ai-advent-pipeline-mcp", "0.1.0", "dir"), market);
        marketServer.start();

        Day18McpClient schedulerClient = new Day18McpClient(
                new Day18Properties(schedulerServer.boundPort(), "/mcp", "ai-advent-scheduler-mcp",
                        "0.1.0", "dir", 50L));
        Day19McpClient marketClient = new Day19McpClient(
                new Day19Properties(marketServer.boundPort(), "/mcp", "ai-advent-pipeline-mcp",
                        "0.1.0", "dir"));
        Day20Orchestrator orchestrator = new Day20Orchestrator(schedulerClient, marketClient,
                new Day18Properties(schedulerServer.boundPort(), "/mcp", "ai-advent-scheduler-mcp",
                        "0.1.0", "dir", 50L),
                new Day19Properties(marketServer.boundPort(), "/mcp", "ai-advent-pipeline-mcp",
                        "0.1.0", "dir"));

        server = new Day20McpServer(
                new Day20Properties(0, "/mcp", "ai-advent-orchestrator-mcp", "0.1.0"), orchestrator);
        server.start();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        url = "http://localhost:" + server.boundPort() + "/mcp";
    }

    @AfterEach
    void tearDown() {
        server.stop();
        schedulerServer.stop();
        marketServer.stop();
    }

    private HttpResponse<String> post(String body, String sessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (sessionId != null) {
            builder.header(Day20McpServer.SESSION_HEADER, sessionId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String sessionId() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\"}}", null);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().firstValue(Day20McpServer.SESSION_HEADER).orElse(null);
    }

    private String callToolText(String tool, String arguments, String session) throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}}", session);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = mapper.readTree(response.body());
        if (json.has("error")) {
            throw new AssertionError("tools/call вернул ошибку: " + json.get("error").get("message").asText());
        }
        return json.path("result").path("content").get(0).path("text").asText();
    }

    @Test
    void toolsListReturnsTheFiveOrchestratorTools() throws Exception {
        String session = sessionId();
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}",
                session);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = mapper.readTree(response.body());
        assertThat(json.path("result").path("tools")).hasSize(5);
        assertThat(json.path("result").path("tools").get(0).path("name").asText())
                .isEqualTo("orchestrator_servers");
        assertThat(json.path("result").path("tools").get(4).path("name").asText())
                .isEqualTo("orchestrator_run_flow");
    }

    @Test
    void orchestratorServersListsBothRegisteredServers() throws Exception {
        String session = sessionId();
        String text = callToolText("orchestrator_servers", "{}", session);

        assertThat(text).contains("scheduler").contains("market").contains("Оркестратор");
    }

    @Test
    void orchestratorRouteSendsToolToTheRightServer() throws Exception {
        String session = sessionId();
        String text = callToolText("orchestrator_route",
                "{\"tool\":\"search\",\"arguments\":{\"query\":\"ноутбук\"}}", session);

        assertThat(text).contains("\"products\"").contains("Lenovo");
    }

    @Test
    void orchestratorCallInvokesToolOnNamedServer() throws Exception {
        String session = sessionId();
        String text = callToolText("orchestrator_call",
                "{\"server\":\"scheduler\",\"tool\":\"scheduler_list_jobs\"}", session);

        assertThat(text).contains("j-100");
    }

    @Test
    void orchestratorRunFlowExecutesCrossServerFlow() throws Exception {
        String session = sessionId();
        String text = callToolText("orchestrator_run_flow",
                "{\"flow\":\"market-report\",\"arguments\":{\"query\":\"телевизор\","
                        + "\"format\":\"csv\",\"fileName\":\"тв-2026\"}}", session);

        assertThat(text).contains("Флоу «market-report»").contains("успешно: 4/4");
    }

    @Test
    void orchestratorRouteWithUnknownToolFailsWithInvalidParams() throws Exception {
        String session = sessionId();
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"orchestrator_route\",\"arguments\":{\"tool\":\"calculator\"}}}",
                session);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = mapper.readTree(response.body());
        assertThat(json.path("error").path("code").asInt()).isEqualTo(-32602);
        assertThat(json.path("error").path("message").asText()).contains("не зарегистрирован");
    }

    @Test
    void unknownToolNameFailsWithMethodNotFound() throws Exception {
        String session = sessionId();
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"summarize\"}}", session);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = mapper.readTree(response.body());
        assertThat(json.path("error").path("code").asInt()).isEqualTo(-32601);
        assertThat(json.path("error").path("message").asText()).contains("Инструмент не найден");
    }
}