package com.yunovan.aiadvent.day34;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Day34McpClient {

    private static final Logger log = LoggerFactory.getLogger(Day34McpClient.class);

    private static final Pattern LIST_LINE = Pattern.compile("^(.*?)\\t(\\d+)\\t(\\d+)$");
    private static final Pattern GREP_LINE = Pattern.compile("^([^:]+):(\\d+): ?(.*)$");
    private static final Pattern WRITE_LINE = Pattern.compile("^(\\S+)\\s+(\\S+)\\s+\\((\\d+) байт\\)$");
    private static final Pattern STAT_LINE = Pattern.compile("^(.*?): (\\d+) строк, (\\d+) байт$");
    private static final Pattern PATCH_LINE = Pattern.compile("^update\\s+(\\S+)$");

    private final Day34Properties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;

    public Day34McpClient(Day34Properties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public Day34Connection connect() {
        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("method", "initialize");
        request.put("id", "init");
        request.putObject("params").put("protocolVersion", Day34McpServer.PROTOCOL_VERSION);
        HttpRequest httpRequest = baseRequest("")
                .POST(HttpRequest.BodyPublishers.ofString(request.toString()))
                .build();
        HttpResponse<String> raw = send(httpRequest);
        JsonNode json = parse(raw.body());
        if (json.path("error").isObject()) {
            throw new Day34McpException("Не удалось установить сессию: "
                    + json.path("error").path("message").asText());
        }
        String sessionId = raw.headers().firstValue(Day34McpServer.SESSION_HEADER).orElse("");
        if (sessionId.isBlank()) {
            throw new Day34McpException("Сервер не вернул Mcp-Session-Id");
        }
        log.info("День 34: подключение к MCP-серверу файлов: {} (сессия {})", uri(), sessionId);
        return new Day34Connection(
                Day34McpServer.PROTOCOL_VERSION, properties.name(), properties.version(), sessionId);
    }

    public List<Day34ToolInfo> listTools(Day34Connection connection) {
        JsonNode response = jsonRpc(connection, "tools/list", mapper.createObjectNode());
        List<Day34ToolInfo> result = new ArrayList<>();
        for (JsonNode tool : response.path("result").path("tools")) {
            result.add(new Day34ToolInfo(tool.path("name").asText(), tool.path("description").asText()));
        }
        return result;
    }

    public Day34ToolResult callTool(Day34Connection connection, String tool, Map<String, Object> arguments) {
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
        return new Day34ToolResult(tool, text.toString(), isError);
    }

    public List<Day34FileInfo> listFiles(Day34Connection connection, String prefix, Integer limit) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("prefix", prefix);
        args.put("limit", limit);
        String output = callTool(connection, "fs_list", args).content();
        List<Day34FileInfo> files = new ArrayList<>();
        for (String line : output.split("\n")) {
            Matcher matcher = LIST_LINE.matcher(line.strip());
            if (matcher.matches()) {
                files.add(new Day34FileInfo(matcher.group(1),
                        Long.parseLong(matcher.group(3)), Integer.parseInt(matcher.group(2)), ""));
            } else if (!line.isBlank()) {
                files.add(new Day34FileInfo(line.strip(), -1, -1, ""));
            }
        }
        return files;
    }

    public String readFile(Day34Connection connection, String path, Integer startLine, Integer maxLines) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", path);
        args.put("startLine", startLine);
        args.put("maxLines", maxLines);
        return callTool(connection, "fs_read", args).content();
    }

    public List<Day34SearchMatch> search(Day34Connection connection, String pattern, String glob,
            Integer maxResults) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("pattern", pattern);
        args.put("glob", glob);
        args.put("maxResults", maxResults);
        String output = callTool(connection, "fs_grep", args).content();
        List<Day34SearchMatch> matches = new ArrayList<>();
        for (String line : output.split("\n")) {
            Matcher matcher = GREP_LINE.matcher(line);
            if (matcher.matches()) {
                matches.add(new Day34SearchMatch(matcher.group(1),
                        Integer.parseInt(matcher.group(2)), matcher.group(3)));
            }
        }
        return matches;
    }

    public Day34WriteResult writeFile(Day34Connection connection, String path, String content) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", path);
        args.put("content", content);
        String output = callTool(connection, "fs_write", args).content();
        Matcher matcher = WRITE_LINE.matcher(output.strip());
        if (matcher.matches()) {
            return new Day34WriteResult(matcher.group(2), matcher.group(1),
                    Integer.parseInt(matcher.group(3)));
        }
        if (output.strip().startsWith("create")) {
            return new Day34WriteResult(path, "create", content == null ? 0 : content.length());
        }
        return new Day34WriteResult(path, "update", content == null ? 0 : content.length());
    }

    public String patchFile(Day34Connection connection, String path, String search, String replace) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", path);
        args.put("search", search);
        args.put("replace", replace);
        String output = callTool(connection, "fs_patch", args).content();
        Matcher matcher = PATCH_LINE.matcher(output.strip());
        return matcher.matches() ? matcher.group(1) : path;
    }

    public Day34FileInfo stat(Day34Connection connection, String path) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", path);
        String output = callTool(connection, "fs_stat", args).content();
        Matcher matcher = STAT_LINE.matcher(output.strip());
        if (matcher.matches()) {
            return new Day34FileInfo(matcher.group(1),
                    Long.parseLong(matcher.group(3)), Integer.parseInt(matcher.group(2)), "");
        }
        return new Day34FileInfo(path, -1, -1, "");
    }

    private JsonNode jsonRpc(Day34Connection connection, String method, JsonNode params) {
        if (connection == null || connection.sessionId().isBlank()) {
            throw new Day34McpException("Сначала установите сессию (connect)");
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
            throw new Day34McpException(json.path("error").path("message").asText());
        }
        if (!json.has("result")) {
            throw new Day34McpException("Ответ MCP не содержит result");
        }
        return json;
    }

    private HttpRequest.Builder baseRequest(String sessionId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20));
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header(Day34McpServer.SESSION_HEADER, sessionId);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new Day34McpException("Прервано при отправке запроса MCP: " + ex.getMessage(), ex);
        } catch (Day34McpException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new Day34McpException(
                    "Не удалось отправить запрос MCP на " + uri() + ": " + ex.getMessage(), ex);
        }
    }

    private JsonNode parse(String body) {
        try {
            return mapper.readTree(body);
        } catch (Exception ex) {
            throw new Day34McpException("Некорректный ответ MCP: " + ex.getMessage(), ex);
        }
    }

    private URI uri() {
        return URI.create("http://localhost:" + properties.serverPort() + properties.path());
    }
}
