package com.yunovan.aiadvent.day16;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day16McpServerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    private Day16McpServer server;
    private HttpClient http;
    private String url;

    @BeforeEach
    void setUp() {
        server = new Day16McpServer(new Day16Properties(0, "/mcp", "ai-advent-mcp", "0.1.0"));
        server.start();
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        url = "http://localhost:" + server.port() + "/mcp";
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> post(String body, String sessionId) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (sessionId != null) {
            builder.header(Day16McpServer.SESSION_HEADER, sessionId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String sessionId() throws Exception {
        HttpResponse<String> response = post(initializeRequest(), null);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().firstValue(Day16McpServer.SESSION_HEADER).orElse(null);
    }

    private static String initializeRequest() {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":"
                + "{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"test\",\"version\":\"1.0\"}}}";
    }

    private static String request(int id, String method) {
        return "{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"method\":\"" + method + "\"}";
    }

    @Test
    void initializeEstablishesConnectionAndReturnsProtocolVersion() throws Exception {
        HttpResponse<String> response = post(initializeRequest(), null);

        assertThat(response.statusCode()).isEqualTo(200);
        String sessionId = response.headers().firstValue(Day16McpServer.SESSION_HEADER).orElse(null);
        assertThat(sessionId).isNotBlank();
        JsonNode json = mapper.readTree(response.body());
        assertThat(json.path("jsonrpc").asText()).isEqualTo("2.0");
        JsonNode result = json.path("result");
        assertThat(result.path("protocolVersion").asText()).isEqualTo("2024-11-05");
        assertThat(result.path("serverInfo").path("name").asText()).isEqualTo("ai-advent-mcp");
        assertThat(result.path("serverInfo").path("version").asText()).isEqualTo("0.1.0");
        assertThat(result.path("capabilities").path("tools").isObject()).isTrue();
    }

    @Test
    void initializedNotificationReturns202() throws Exception {
        HttpResponse<String> response = post(
                "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", sessionId());

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void toolsListReturnsAvailableTools() throws Exception {
        HttpResponse<String> response = post(request(2, "tools/list"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode tools = mapper.readTree(response.body()).path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(2);
        assertThat(tools.get(0).path("name").asText()).isEqualTo("day16_sum");
        assertThat(tools.get(0).path("description").asText()).isNotBlank();
        assertThat(tools.get(0).path("inputSchema").path("type").asText()).isEqualTo("object");
        assertThat(tools.get(0).path("inputSchema").path("required").toString())
                .contains("a").contains("b");
        assertThat(tools.get(1).path("name").asText()).isEqualTo("day16_upper");
        assertThat(tools.get(1).path("description").asText()).isNotBlank();
    }

    @Test
    void toolsCallExecutesToolAndReturnsContent() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"day16_sum\",\"arguments\":{\"a\":6,\"b\":7}}}", sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode result = mapper.readTree(response.body()).path("result");
        assertThat(result.path("isError").asBoolean()).isFalse();
        assertThat(result.path("content").get(0).path("type").asText()).isEqualTo("text");
        assertThat(result.path("content").get(0).path("text").asText()).isEqualTo("13");
    }

    @Test
    void toolsCallWithMissingArgumentMarksError() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"day16_sum\",\"arguments\":{}}}", sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode result = mapper.readTree(response.body()).path("result");
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").get(0).path("text").asText()).contains("Отсутствует аргумент: a");
    }

    @Test
    void toolsCallUnknownToolMarksError() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"missing_tool\",\"arguments\":{}}}", sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode result = mapper.readTree(response.body()).path("result");
        assertThat(result.path("isError").asBoolean()).isTrue();
        assertThat(result.path("content").get(0).path("text").asText()).contains("missing_tool");
    }

    @Test
    void toolsListWithoutSessionIsRejected() throws Exception {
        HttpResponse<String> response = post(request(6, "tools/list"), null);

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode json = mapper.readTree(response.body());
        assertThat(json.path("error").path("code").asInt()).isEqualTo(-32600);
        assertThat(json.path("error").path("message").asText()).isEqualTo("Неизвестная сессия MCP");
    }

    @Test
    void unknownMethodReturnsMethodNotFoundError() throws Exception {
        HttpResponse<String> response = post(request(7, "bogus"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = mapper.readTree(response.body());
        assertThat(json.path("error").path("code").asInt()).isEqualTo(-32601);
        assertThat(json.path("error").path("message").asText()).contains("bogus");
    }

    @Test
    void pingReturnsEmptyResult() throws Exception {
        HttpResponse<String> response = post(request(8, "ping"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = mapper.readTree(response.body());
        assertThat(json.path("id").asInt()).isEqualTo(8);
        assertThat(json.path("result").isObject()).isTrue();
    }

    @Test
    void invalidJsonReturnsParseError() throws Exception {
        HttpResponse<String> response = post("{not-json", sessionId());

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode json = mapper.readTree(response.body());
        assertThat(json.path("error").path("code").asInt()).isEqualTo(-32700);
    }
}