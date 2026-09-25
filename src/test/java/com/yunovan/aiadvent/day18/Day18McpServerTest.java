package com.yunovan.aiadvent.day18;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day18McpServerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Day18McpServer server;
    private Day18SchedulerService scheduler;
    private Day18Store store;
    private HttpClient http;
    private String url;

    @BeforeEach
    void setUp() {
        store = new Day18Store(tempDir);
        scheduler = new Day18SchedulerService(store,
                new Day18Properties(0, "/mcp", "ai-advent-scheduler-mcp", "0.1.0", "dir", 50L),
                new Day18HttpProbe());
        server = new Day18McpServer(
                new Day18Properties(0, "/mcp", "ai-advent-scheduler-mcp", "0.1.0", "dir", 50L), scheduler);
        server.start();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        url = "http://localhost:" + server.boundPort() + "/mcp";
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> post(String body, String sessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (sessionId != null) {
            builder.header(Day18McpServer.SESSION_HEADER, sessionId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String sessionId() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\"}}", null);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().firstValue(Day18McpServer.SESSION_HEADER).orElse(null);
    }

    private static String request(int id, String method) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\"}";
    }

    private static String call(int id, String tool, String arguments) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}}";
    }

    private String callToolText(String tool, String arguments, String session) throws Exception {
        HttpResponse<String> response = post(call(9, tool, arguments), session);
        assertThat(response.statusCode()).isEqualTo(200);
        return mapper.readTree(response.body()).path("result").path("content").get(0).path("text").asText();
    }

    private static String extractId(String json) {
        String key = "\"id\":\"";
        int start = json.indexOf(key) + key.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }

    @Test
    void initializeEstablishesConnectionAndReturnsServerInfo() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\"}}", null);

        assertThat(response.statusCode()).isEqualTo(200);
        String sessionId = response.headers().firstValue(Day18McpServer.SESSION_HEADER).orElse(null);
        assertThat(sessionId).isNotBlank();
        JsonNode result = mapper.readTree(response.body()).path("result");
        assertThat(result.path("protocolVersion").asText()).isEqualTo("2024-11-05");
        assertThat(result.path("serverInfo").path("name").asText()).isEqualTo("ai-advent-scheduler-mcp");
        assertThat(result.path("serverInfo").path("version").asText()).isEqualTo("0.1.0");
        assertThat(result.path("capabilities").path("tools").isObject()).isTrue();
    }

    @Test
    void toolsListReturnsSixSchedulerTools() throws Exception {
        HttpResponse<String> response = post(request(2, "tools/list"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode tools = mapper.readTree(response.body()).path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(6);
        assertThat(tools.get(0).path("name").asText()).isEqualTo("scheduler_add_reminder");
        assertThat(tools.get(0).path("inputSchema").path("type").asText()).isEqualTo("object");
        assertThat(tools.get(0).path("inputSchema").path("required").toString()).contains("delaySeconds");
        assertThat(tools.get(1).path("name").asText()).isEqualTo("scheduler_add_collector");
        assertThat(tools.get(1).path("inputSchema").path("required").toString())
                .contains("feed").contains("periodSeconds");
        assertThat(tools.get(2).path("name").asText()).isEqualTo("scheduler_list_jobs");
        assertThat(tools.get(3).path("name").asText()).isEqualTo("scheduler_summary");
        assertThat(tools.get(4).path("name").asText()).isEqualTo("scheduler_run_now");
        assertThat(tools.get(5).path("name").asText()).isEqualTo("scheduler_stop_process");
    }

    @Test
    void callAddReminderReturnsScheduledJob() throws Exception {
        String text = callToolText("scheduler_add_reminder",
                "{\"topic\":\"выпить чай\",\"delaySeconds\":30}", sessionId());

        assertThat(text).contains("\"type\":\"reminder\"");
        assertThat(text).contains("\"delaySeconds\":30");
        assertThat(text).contains("\"status\":\"pending\"");
        assertThat(text).contains("\"feed\":\"reminders\"");
    }

    @Test
    void callAddReminderWithoutDelayMarksError() throws Exception {
        HttpResponse<String> response = post(call(4, "scheduler_add_reminder", "{}"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32602);
        assertThat(error.path("message").asText()).contains("delaySeconds");
    }

    @Test
    void callAddCollectorReturnsPeriodicJob() throws Exception {
        String text = callToolText("scheduler_add_collector",
                "{\"feed\":\"events\",\"periodSeconds\":5}", sessionId());

        assertThat(text).contains("\"type\":\"collector\"");
        assertThat(text).contains("\"periodSeconds\":5");
        assertThat(text).contains("\"status\":\"active\"");
        assertThat(text).contains("\"feed\":\"events\"");
    }

    @Test
    void callRunNowExecutesCollectorImmediately() throws Exception {
        String created = callToolText("scheduler_add_collector",
                "{\"feed\":\"events\",\"periodSeconds\":60}", sessionId());
        String jobId = extractId(created);

        String text = callToolText("scheduler_run_now", "{\"jobId\":\"" + jobId + "\"}", sessionId());

        assertThat(text).contains("\"runCount\":1");
        assertThat(store.samplesFor("events", null)).hasSize(1);
        assertThat(store.samplesFor("events", null).get(0).value()).isEqualTo(1.0);
    }

    @Test
    void callStopProcessStopsCollector() throws Exception {
        String created = callToolText("scheduler_add_collector",
                "{\"feed\":\"events\",\"periodSeconds\":60}", sessionId());
        String jobId = extractId(created);

        String text = callToolText("scheduler_stop_process", "{\"jobId\":\"" + jobId + "\"}", sessionId());

        assertThat(text).contains("\"status\":\"stopped\"");
        assertThat(text).contains("\"id\":\"" + jobId + "\"");
        assertThat(store.findById(jobId).getNextRunAt()).isNull();
    }

    @Test
    void callStopProcessWithoutIdStopsAll() throws Exception {
        String session = sessionId();
        String first = callToolText("scheduler_add_collector",
                "{\"feed\":\"events\",\"periodSeconds\":60}", session);
        String second = callToolText("scheduler_add_collector",
                "{\"feed\":\"uptime\",\"periodSeconds\":60}", session);

        String text = callToolText("scheduler_stop_process", "{}", session);

        assertThat(text).contains(extractId(first));
        assertThat(text).contains(extractId(second));
        assertThat(text).contains("\"status\":\"stopped\"");
        assertThat(store.findById(extractId(first)).getStatus()).isEqualTo("stopped");
        assertThat(store.findById(extractId(second)).getStatus()).isEqualTo("stopped");
    }

    @Test
    void callStopProcessWithUnknownIdMarksError() throws Exception {
        HttpResponse<String> response = post(call(12, "scheduler_stop_process",
                "{\"jobId\":\"j-missing\"}"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32602);
        assertThat(error.path("message").asText()).contains("Задание не найдено");
    }

    @Test
    void callSummaryAggregatesCollectedSamples() throws Exception {
        String session = sessionId();
        String created = callToolText("scheduler_add_collector",
                "{\"feed\":\"events\",\"periodSeconds\":60}", session);
        String jobId = extractId(created);
        callToolText("scheduler_run_now", "{\"jobId\":\"" + jobId + "\"}", session);
        callToolText("scheduler_run_now", "{\"jobId\":\"" + jobId + "\"}", session);

        String text = callToolText("scheduler_summary", "{\"feed\":\"events\"}", session);

        assertThat(text).contains("\"count\":2");
        assertThat(text).contains("\"avgValue\":1.5");
        assertThat(text).contains("\"minValue\":1.0");
        assertThat(text).contains("\"maxValue\":2.0");
    }

    @Test
    void callUnknownToolReturnsMethodNotFound() throws Exception {
        HttpResponse<String> response = post(call(10, "missing_tool", "{}"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32601);
        assertThat(error.path("message").asText()).contains("missing_tool");
    }

    @Test
    void toolsListWithoutSessionIsRejected() throws Exception {
        HttpResponse<String> response = post(request(11, "tools/list"), null);

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32600);
        assertThat(error.path("message").asText()).contains("initialize");
    }
}