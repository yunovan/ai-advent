package com.yunovan.aiadvent.day17;

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

class Day17McpServerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Day17McpServer server;
    private HttpClient http;
    private String url;

    @BeforeEach
    void setUp() {
        server = new Day17McpServer(
                new Day17Properties(0, "/mcp", "ai-advent-tracker-mcp", "0.1.0", "dir"),
                new Day17TrackerService(new Day17TicketStore(tempDir)));
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
            builder.header(Day17McpServer.SESSION_HEADER, sessionId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String sessionId() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\"}}", null);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().firstValue(Day17McpServer.SESSION_HEADER).orElse(null);
    }

    private static String request(int id, String method) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\"}";
    }

    private static String call(int id, String tool, String arguments) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}}";
    }

    @Test
    void initializeEstablishesConnectionAndReturnsServerInfo() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\"}}", null);

        assertThat(response.statusCode()).isEqualTo(200);
        String sessionId = response.headers().firstValue(Day17McpServer.SESSION_HEADER).orElse(null);
        assertThat(sessionId).isNotBlank();
        JsonNode result = mapper.readTree(response.body()).path("result");
        assertThat(result.path("protocolVersion").asText()).isEqualTo("2024-11-05");
        assertThat(result.path("serverInfo").path("name").asText()).isEqualTo("ai-advent-tracker-mcp");
        assertThat(result.path("serverInfo").path("version").asText()).isEqualTo("0.1.0");
        assertThat(result.path("capabilities").path("tools").isObject()).isTrue();
    }

    @Test
    void toolsListReturnsThreeTrackerTools() throws Exception {
        HttpResponse<String> response = post(request(2, "tools/list"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode tools = mapper.readTree(response.body()).path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(3);
        assertThat(tools.get(0).path("name").asText()).isEqualTo("tracker_create_task");
        assertThat(tools.get(0).path("description").asText()).isNotBlank();
        assertThat(tools.get(0).path("inputSchema").path("type").asText()).isEqualTo("object");
        assertThat(tools.get(0).path("inputSchema").path("required").toString()).contains("title");
        assertThat(tools.get(1).path("name").asText()).isEqualTo("tracker_list_tasks");
        assertThat(tools.get(2).path("name").asText()).isEqualTo("tracker_add_comment");
        assertThat(tools.get(2).path("inputSchema").path("required").toString())
                .contains("taskId").contains("text");
    }

    @Test
    void callCreateTaskReturnsCreatedTicket() throws Exception {
        HttpResponse<String> response = post(call(3, "tracker_create_task",
                "{\"title\":\"Привезти стол\",\"assignee\":\"Мария\",\"description\":\"для офиса\"}"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode result = mapper.readTree(response.body()).path("result");
        assertThat(result.path("isError").asBoolean()).isFalse();
        String text = result.path("content").get(0).path("text").asText();
        assertThat(text).contains("\"id\":\"t-");
        assertThat(text).contains("\"title\":\"Привезти стол\"");
        assertThat(text).contains("\"status\":\"new\"");
        assertThat(text).contains("\"assignee\":\"Мария\"");
    }

    @Test
    void callCreateTaskWithoutTitleMarksError() throws Exception {
        HttpResponse<String> response = post(call(4, "tracker_create_task", "{}"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32602);
        assertThat(error.path("message").asText()).contains("заголовок");
    }

    @Test
    void callListTasksReturnsEmptyArray() throws Exception {
        HttpResponse<String> response = post(call(5, "tracker_list_tasks", "{}"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode result = mapper.readTree(response.body()).path("result");
        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(result.path("content").get(0).path("text").asText()).contains("[]");
    }

    @Test
    void callCreateThenListShowsTask() throws Exception {
        String session = sessionId();
        post(call(6, "tracker_create_task", "{\"title\":\"Купить кресло\"}"), session);

        HttpResponse<String> response = post(call(7, "tracker_list_tasks", "{\"status\":\"new\"}"), session);

        String text = mapper.readTree(response.body()).path("result").path("content").get(0).path("text").asText();
        assertThat(text).contains("Купить кресло");
        assertThat(text).contains("\"status\":\"new\"");
    }

    @Test
    void callAddCommentToUnknownTaskMarksError() throws Exception {
        HttpResponse<String> response = post(call(8, "tracker_add_comment",
                "{\"taskId\":\"t-none\",\"text\":\"текст\"}"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32602);
        assertThat(error.path("message").asText()).contains("не найдена");
    }

    @Test
    void callUnknownToolReturnsMethodNotFound() throws Exception {
        HttpResponse<String> response = post(call(9, "missing_tool", "{}"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32601);
        assertThat(error.path("message").asText()).contains("missing_tool");
    }

    @Test
    void toolsListWithoutSessionIsRejected() throws Exception {
        HttpResponse<String> response = post(request(10, "tools/list"), null);

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32600);
        assertThat(error.path("message").asText()).contains("initialize");
    }

    @Test
    void nonPostMethodReturnsNotAllowed() throws Exception {
        HttpRequest getRequest = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> response = http.send(getRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(405);
    }
}