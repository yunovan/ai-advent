package com.yunovan.aiadvent.day19;

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

class Day19McpServerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Day19McpServer server;
    private Day19MarketService market;
    private HttpClient http;
    private String url;

    @BeforeEach
    void setUp() {
        Day19SaveService saveService = new Day19SaveService(
                new Day19Properties(0, "/mcp", "ai-advent-pipeline-mcp", "0.1.0", tempDir.toString()));
        market = new Day19MarketService(new Day19CatalogService(), saveService);
        server = new Day19McpServer(
                new Day19Properties(0, "/mcp", "ai-advent-pipeline-mcp", "0.1.0", "dir"), market);
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
            builder.header(Day19McpServer.SESSION_HEADER, sessionId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String sessionId() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\"}}", null);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().firstValue(Day19McpServer.SESSION_HEADER).orElse(null);
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

    @Test
    void initializeEstablishesConnectionAndReturnsServerInfo() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\"}}", null);

        assertThat(response.statusCode()).isEqualTo(200);
        String sessionId = response.headers().firstValue(Day19McpServer.SESSION_HEADER).orElse(null);
        assertThat(sessionId).isNotBlank();
        JsonNode result = mapper.readTree(response.body()).path("result");
        assertThat(result.path("protocolVersion").asText()).isEqualTo("2024-11-05");
        assertThat(result.path("serverInfo").path("name").asText()).isEqualTo("ai-advent-pipeline-mcp");
        assertThat(result.path("serverInfo").path("version").asText()).isEqualTo("0.1.0");
        assertThat(result.path("capabilities").path("tools").isObject()).isTrue();
    }

    @Test
    void toolsListReturnsThreePipelineTools() throws Exception {
        HttpResponse<String> response = post(request(2, "tools/list"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode tools = mapper.readTree(response.body()).path("result").path("tools");
        assertThat(tools.isArray()).isTrue();
        assertThat(tools.size()).isEqualTo(3);
        assertThat(tools.get(0).path("name").asText()).isEqualTo("search");
        assertThat(tools.get(0).path("inputSchema").path("type").asText()).isEqualTo("object");
        assertThat(tools.get(0).path("inputSchema").path("required").toString()).contains("query");
        assertThat(tools.get(1).path("name").asText()).isEqualTo("summarize");
        assertThat(tools.get(1).path("inputSchema").path("required").toString()).contains("data");
        assertThat(tools.get(2).path("name").asText()).isEqualTo("saveToFile");
    }

    @Test
    void callSearchReturnsProductsJson() throws Exception {
        String text = callToolText("search", "{\"query\":\"ноутбук\"}", sessionId());

        JsonNode root = mapper.readTree(text);
        assertThat(root.path("products").isArray()).isTrue();
        assertThat(root.path("products")).hasSize(5);
        assertThat(root.path("products").get(0).path("id").asText()).isEqualTo("p01");
        assertThat(root.path("products").get(0).path("price").asDouble()).isEqualTo(54990.0);
        assertThat(root.path("products").get(0).path("params").path("Память").asText()).isEqualTo("16 ГБ");
    }

    @Test
    void callSumarizeBuildsMarkdownTableFromSearchData() throws Exception {
        String session = sessionId();
        String data = callToolText("search", "{\"query\":\"ноутбук\"}", session);

        String text = callToolText("summarize",
                "{\"query\":\"ноутбук\",\"data\":" + mapper.writeValueAsString(data) + "}", session);

        assertThat(text).startsWith("Сравнение по запросу «ноутбук» — товаров: 5");
        assertThat(text).contains("Товар | Продавец | Цена, ₽ | Рейтинг");
    }

    @Test
    void callSumarizeWithoutDataReturnsInvalidParams() throws Exception {
        HttpResponse<String> response = post(call(3, "summarize", "{\"query\":\"ноутбук\"}"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32602);
        assertThat(error.path("message").asText()).contains("пуст");
    }

    @Test
    void callSunmarizeWithBrokenDataReturnsInvalidParams() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode arguments = mapper.createObjectNode();
        arguments.put("query", "ноутбук");
        arguments.put("data", "{\"products\":[");
        com.fasterxml.jackson.databind.node.ObjectNode params = mapper.createObjectNode();
        params.put("name", "summarize");
        params.set("arguments", arguments);
        com.fasterxml.jackson.databind.node.ObjectNode call = mapper.createObjectNode();
        call.put("jsonrpc", "2.0");
        call.put("id", 4);
        call.put("method", "tools/call");
        call.set("params", params);

        HttpResponse<String> response = post(call.toString(), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32602);
        assertThat(error.path("message").asText()).contains("некорректен");
    }

    @Test
    void callSaveToFileSavesSummaryAndReturnsFileInfo() throws Exception {
        String session = sessionId();
        String data = callToolText("search", "{\"query\":\"ноутбук\"}", session);
        String summary = callToolText("summarize",
                "{\"query\":\"ноутбук\",\"data\":" + mapper.writeValueAsString(data) + "}", session);

        String text = callToolText("saveToFile", "{\"data\":"
                + mapper.writeValueAsString(data) + ",\"summary\":"
                + mapper.writeValueAsString(summary) + ",\"format\":\"markdown\",\"fileName\":\"laptops\"}", session);

        JsonNode root = mapper.readTree(text);
        assertThat(root.path("fileName").asText()).isEqualTo("laptops.md");
        assertThat(root.path("bytes").asLong()).isPositive();
        assertThat(root.path("path").asText()).endsWith("laptops.md");
        assertThat(java.nio.file.Files.exists(java.nio.file.Path.of(root.path("path").asText()))).isTrue();
    }

    @Test
    void callSaveToFileWithoutSummaryReturnsInvalidParams() throws Exception {
        HttpResponse<String> response = post(call(5, "saveToFile", "{\"format\":\"markdown\"}"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32602);
        assertThat(error.path("message").asText()).contains("Нечего сохранять");
    }

    @Test
    void callSaveToFileWithUnknownFormatReturnsInvalidParams() throws Exception {
        HttpResponse<String> response = post(call(6, "saveToFile",
                "{\"summary\":\"Сравнение по запросу «ноутбук» — товаров: 5\",\"format\":\"pdf\"}"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32602);
        assertThat(error.path("message").asText()).contains("Неизвестный формат");
    }

    @Test
    void callUnknownToolReturnsMethodNotFound() throws Exception {
        HttpResponse<String> response = post(call(7, "missing_tool", "{}"), sessionId());

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32601);
        assertThat(error.path("message").asText()).contains("missing_tool");
    }

    @Test
    void toolsCallWithoutSessionIsRejected() throws Exception {
        HttpResponse<String> response = post(request(8, "tools/list"), null);

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode error = mapper.readTree(response.body()).path("error");
        assertThat(error.path("code").asInt()).isEqualTo(-32600);
        assertThat(error.path("message").asText()).contains("initialize");
    }
}