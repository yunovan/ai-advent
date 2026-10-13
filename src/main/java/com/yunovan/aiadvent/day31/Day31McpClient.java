package com.yunovan.aiadvent.day31;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Day31McpClient {

    private static final Logger log = LoggerFactory.getLogger(Day31McpClient.class);

    private final Day31Properties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public Day31McpClient(Day31Properties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public Day31Connection connect() {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("method", "initialize");
        request.put("id", "init");
        request.putObject("params").put("protocolVersion", Day31McpServer.PROTOCOL_VERSION);
        HttpRequest httpRequest = baseRequest("")
                .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
                .build();
        HttpResponse<String> raw = send(httpRequest);
        JsonNode json = parse(raw.body());
        if (json.path("error").isObject()) {
            throw new Day31McpException("Не удалось установить сессию: "
                    + json.path("error").path("message").asText());
        }
        String sessionId = raw.headers().firstValue(Day31McpServer.SESSION_HEADER).orElse("");
        if (sessionId.isBlank()) {
            throw new Day31McpException("Сервер не вернул Mcp-Session-Id");
        }
        log.info("День 31: подключение к MCP-серверу проекта: {} (сессия {})", uri(), sessionId);
        return new Day31Connection(
                Day31McpServer.PROTOCOL_VERSION, properties.name(), properties.version(), sessionId);
    }

    public List<Day31ToolInfo> listTools(Day31Connection connection) {
        JsonNode response = jsonRpc(connection, "tools/list", mapper.createObjectNode());
        List<Day31ToolInfo> result = new ArrayList<>();
        for (JsonNode tool : response.path("result").path("tools")) {
            result.add(new Day31ToolInfo(tool.path("name").asText(), tool.path("description").asText()));
        }
        return result;
    }

    public Day31ToolResult callTool(Day31Connection connection, String tool, Map<String, Object> arguments) {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", tool);
        ObjectNode argumentsNode = mapper.createObjectNode();
        if (arguments != null) {
            for (Map.Entry<String, Object> argument : arguments.entrySet()) {
                if (argument.getValue() != null) {
                    argumentsNode.putPOJO(argument.getKey(), argument.getValue());
                }
            }
        }
        params.set("arguments", argumentsNode);
        JsonNode response = jsonRpc(connection, "tools/call", params);
        StringBuilder text = new StringBuilder();
        for (JsonNode content : response.path("result").path("content")) {
            text.append(content.path("text").asText());
        }
        boolean isError = response.path("result").path("isError").asBoolean();
        return new Day31ToolResult(tool, text.toString(), isError);
    }

    private JsonNode jsonRpc(Day31Connection connection, String method, JsonNode params) {
        if (connection == null || connection.sessionId().isBlank()) {
            throw new Day31McpException("Сначала установите сессию (connect)");
        }
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("method", method);
        request.put("id", "req-" + method);
        request.set("params", params);
        HttpRequest httpRequest = baseRequest(connection.sessionId())
                .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
                .build();
        HttpResponse<String> raw = send(httpRequest);
        JsonNode json = parse(raw.body());
        if (json.path("error").isObject()) {
            throw new Day31McpException(json.path("error").path("message").asText());
        }
        if (!json.has("result")) {
            throw new Day31McpException("Ответ MCP не содержит result");
        }
        return json;
    }

    private HttpRequest.Builder baseRequest(String sessionId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20));
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header(Day31McpServer.SESSION_HEADER, sessionId);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Day31McpException("Прервано при отправке запроса MCP: " + ex.getMessage(), ex);
        } catch (Day31McpException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new Day31McpException(
                    "Не удалось отправить запрос MCP на " + uri() + ": " + ex.getMessage(), ex);
        }
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception ex) {
            throw new Day31McpException("Некорректный ответ MCP: " + ex.getMessage(), ex);
        }
    }

    private URI uri() {
        return URI.create("http://localhost:" + properties.serverPort() + properties.path());
    }
}
