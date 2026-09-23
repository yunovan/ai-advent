package com.yunovan.aiadvent.day16;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Day16McpClient {

    private static final Logger log = LoggerFactory.getLogger(Day16McpClient.class);

    private final Day16Properties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public Day16McpClient(Day16Properties properties) {
        this.properties = properties;
    }

    public Day16Connection connect() {
        ObjectNode clientInfo = mapper.createObjectNode()
                .put("name", "ai-advent")
                .put("version", properties.version());
        ObjectNode params = mapper.createObjectNode()
                .put("protocolVersion", Day16McpServer.PROTOCOL_VERSION);
        params.set("capabilities", mapper.createObjectNode());
        params.set("clientInfo", clientInfo);
        ObjectNode body = request("initialize").put("id", 1);
        body.set("params", params);
        HttpResponse<String> response = send(body, null);
        JsonNode json = parse(response.body());
        if (response.statusCode() != 200) {
            throw new Day16McpException(protocolError(response.statusCode(), json, "initialize"));
        }
        JsonNode result = json.path("result");
        if (result.isMissingNode() || result.isNull()) {
            throw new Day16McpException("MCP вернул некорректный ответ initialize: " + json);
        }
        String sessionId = response.headers().firstValue(Day16McpServer.SESSION_HEADER).orElse(null);
        String protocolVersion = result.path("protocolVersion").asText();
        String serverName = result.path("serverInfo").path("name").asText();
        String serverVersion = result.path("serverInfo").path("version").asText();
        notification("notifications/initialized", sessionId);
        return new Day16Connection(protocolVersion, serverName, serverVersion,
                result.path("capabilities"), sessionId);
    }

    public List<Day16ToolInfo> listTools(Day16Connection connection) {
        JsonNode result = exchange("tools/list", null, connection.sessionId());
        List<Day16ToolInfo> tools = new ArrayList<>();
        result.path("tools").forEach(tool -> tools.add(new Day16ToolInfo(
                tool.path("name").asText(), tool.path("description").asText())));
        return tools;
    }

    public Day16ToolResult callTool(Day16Connection connection, String name, Map<String, Object> arguments) {
        ObjectNode params = mapper.createObjectNode()
                .put("name", name)
                .set("arguments", mapper.valueToTree(arguments == null ? Map.of() : arguments));
        JsonNode result = exchange("tools/call", params, connection.sessionId());
        boolean isError = result.path("isError").asBoolean(false);
        JsonNode content = result.path("content");
        String text = (content.isArray() && content.size() > 0)
                ? content.get(0).path("text").asText("")
                : "";
        return new Day16ToolResult(name, text, isError);
    }

    private JsonNode exchange(String method, ObjectNode params, String sessionId) {
        ObjectNode body = request(method).put("id", 1);
        if (params != null) {
            body.set("params", params);
        }
        HttpResponse<String> response = send(body, sessionId);
        JsonNode json = parse(response.body());
        if (response.statusCode() != 200) {
            throw new Day16McpException(protocolError(response.statusCode(), json, method));
        }
        JsonNode result = json.path("result");
        if (result.isMissingNode() || result.isNull()) {
            JsonNode error = json.path("error");
            String message = error.isMissingNode() || error.isNull()
                    ? "Неизвестный ответ MCP: " + json
                    : error.path("message").asText("Ошибка MCP");
            throw new Day16McpException(message);
        }
        return result;
    }

    private void notification(String method, String sessionId) {
        try {
            send(request(method), sessionId);
        } catch (Day16McpException ex) {
            log.warn("День 16: уведомление MCP не отправлено: {}", ex.getMessage());
        }
    }

    private HttpResponse<String> send(ObjectNode body, String sessionId) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri())
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(body), StandardCharsets.UTF_8));
            if (sessionId != null && !sessionId.isBlank()) {
                builder.header(Day16McpServer.SESSION_HEADER, sessionId);
            }
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new Day16McpException("MCP недоступен: " + ex.getMessage(), ex);
        }
    }

    private URI uri() {
        return URI.create("http://localhost:" + properties.serverPort() + properties.path());
    }

    private ObjectNode request(String method) {
        return mapper.createObjectNode().put("jsonrpc", "2.0").put("method", method);
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (IOException ex) {
            throw new Day16McpException("Некорректный ответ MCP: " + body, ex);
        }
    }

    private String protocolError(int status, JsonNode json, String method) {
        String message = json == null || json.isNull()
                ? "HTTP " + status
                : json.path("error").path("message").asText("HTTP " + status);
        return "MCP " + method + " завершился ошибкой: " + message;
    }
}