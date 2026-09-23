package com.yunovan.aiadvent.day16;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Day16McpServer {

    public static final String PROTOCOL_VERSION = "2024-11-05";
    public static final String SESSION_HEADER = "Mcp-Session-Id";

    private static final Logger log = LoggerFactory.getLogger(Day16McpServer.class);
    private static final int JSONRPC_PARSE_ERROR = -32700;
    private static final int JSONRPC_INVALID_REQUEST = -32600;
    private static final int JSONRPC_METHOD_NOT_FOUND = -32601;
    private static final int JSONRPC_INVALID_PARAMS = -32602;

    private final Day16Properties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Day16Tool> tools = new LinkedHashMap<>();
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    private HttpServer server;
    private ExecutorService executor;
    private int boundPort;

    public Day16McpServer(Day16Properties properties) {
        this.properties = properties;
        registerTools();
    }

    @PostConstruct
    public void start() {
        if (server != null) {
            return;
        }
        try {
            executor = Executors.newFixedThreadPool(4);
            server = HttpServer.create(new InetSocketAddress(properties.serverPort()), 64);
            server.createContext(properties.path(), this::handle);
            server.setExecutor(executor);
            server.start();
            boundPort = server.getAddress().getPort();
            log.info("День 16: MCP-сервер запущен на http://localhost:{}{}", boundPort, properties.path());
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось запустить MCP-сервер на порту " + properties.serverPort(), ex);
        }
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
        sessions.clear();
    }

    public int port() {
        return server == null ? 0 : boundPort;
    }

    public boolean isRunning() {
        return server != null;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, null);
                return;
            }
            JsonNode request = parseRequest(exchange.getRequestBody().readAllBytes());
            if (request == null) {
                respond(exchange, 400, errorNode(null, JSONRPC_PARSE_ERROR, "Некорректный JSON-RPC запрос"));
                return;
            }
            String method = request.path("method").asText("");
            JsonNode params = request.get("params");
            JsonNode id = request.get("id");
            switch (method) {
                case "initialize" -> handleInitialize(exchange, id);
                case "notifications/initialized" -> respond(exchange, 202, null);
                case "ping" -> respond(exchange, 200, responseNode(id, mapper.createObjectNode()));
                case "tools/list" -> handleToolsList(exchange, id);
                case "tools/call" -> handleToolsCall(exchange, id, params);
                default -> respond(exchange, 200, errorNode(id, JSONRPC_METHOD_NOT_FOUND, "Метод не найден: " + method));
            }
        } catch (Exception ex) {
            log.warn("День 16: MCP-запрос завершился с ошибкой: {}", ex.getMessage());
            respond(exchange, 500, null);
        }
    }

    private void handleInitialize(HttpExchange exchange, JsonNode id) throws IOException {
        String sessionId = UUID.randomUUID().toString();
        sessions.add(sessionId);
        ObjectNode capabilities = mapper.createObjectNode()
                .set("tools", mapper.createObjectNode().put("listChanged", false));
        ObjectNode serverInfo = mapper.createObjectNode()
                .put("name", properties.name())
                .put("version", properties.version());
        ObjectNode result = mapper.createObjectNode().put("protocolVersion", PROTOCOL_VERSION);
        result.set("capabilities", capabilities);
        result.set("serverInfo", serverInfo);
        exchange.getResponseHeaders().set(SESSION_HEADER, sessionId);
        respond(exchange, 200, responseNode(id, result));
    }

    private void handleToolsList(HttpExchange exchange, JsonNode id) throws IOException {
        if (!requireSession(exchange, id)) {
            return;
        }
        ArrayNode list = mapper.createArrayNode();
        for (Day16Tool tool : tools.values()) {
            list.add(mapper.createObjectNode()
                    .put("name", tool.name())
                    .put("description", tool.description())
                    .set("inputSchema", tool.inputSchema()));
        }
        ObjectNode result = mapper.createObjectNode().set("tools", list);
        respond(exchange, 200, responseNode(id, result));
    }

    private void handleToolsCall(HttpExchange exchange, JsonNode id, JsonNode params) throws IOException {
        if (!requireSession(exchange, id)) {
            return;
        }
        String name = params == null ? "" : params.path("name").asText("");
        Map<String, Object> arguments = arguments(params);
        Day16Tool tool = tools.get(name);
        String text;
        boolean isError;
        if (tool == null) {
            text = "Инструмент не найден: " + name;
            isError = true;
        } else {
            try {
                text = tool.handler().apply(arguments);
                isError = false;
            } catch (IllegalArgumentException ex) {
                text = ex.getMessage() == null ? "Некорректные аргументы" : ex.getMessage();
                isError = true;
            }
        }
        ObjectNode contentItem = mapper.createObjectNode().put("type", "text").put("text", text);
        ObjectNode result = mapper.createObjectNode()
                .put("isError", isError)
                .set("content", mapper.createArrayNode().add(contentItem));
        respond(exchange, 200, responseNode(id, result));
    }

    private boolean requireSession(HttpExchange exchange, JsonNode id) throws IOException {
        String sessionId = exchange.getRequestHeaders().getFirst(SESSION_HEADER);
        if (sessionId == null || !sessions.contains(sessionId)) {
            respond(exchange, 400, errorNode(id, JSONRPC_INVALID_REQUEST, "Неизвестная сессия MCP"));
            return false;
        }
        return true;
    }

    private Map<String, Object> arguments(JsonNode params) {
        if (params == null) {
            return Map.of();
        }
        JsonNode arguments = params.get("arguments");
        if (arguments == null || !arguments.isObject()) {
            return Map.of();
        }
        return mapper.convertValue(arguments, Map.class);
    }

    private JsonNode parseRequest(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(body);
            if (node == null || node.isNull() || !node.isObject() || !node.has("method")) {
                return null;
            }
            return node;
        } catch (IOException ex) {
            return null;
        }
    }

    private ObjectNode responseNode(JsonNode id, JsonNode result) {
        ObjectNode node = mapper.createObjectNode().put("jsonrpc", "2.0");
        node.set("id", id);
        node.set("result", result);
        return node;
    }

    private ObjectNode errorNode(JsonNode id, int code, String message) {
        ObjectNode node = mapper.createObjectNode().put("jsonrpc", "2.0");
        node.set("id", id);
        node.set("error", mapper.createObjectNode().put("code", code).put("message", message));
        return node;
    }

    private void respond(HttpExchange exchange, int status, JsonNode body) throws IOException {
        byte[] bytes = body == null ? new byte[0] : mapper.writeValueAsBytes(body);
        if (bytes.length > 0) {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private void registerTools() {
        tools.put("day16_sum", new Day16Tool(
                "day16_sum",
                "Складывает два целых числа и возвращает результат.",
                objectSchema(List.of(
                        new Param("a", "integer", "Первое число"),
                        new Param("b", "integer", "Второе число")), List.of("a", "b")),
                args -> {
                    int a = intArg(args, "a");
                    int b = intArg(args, "b");
                    return String.valueOf(a + b);
                }));
        tools.put("day16_upper", new Day16Tool(
                "day16_upper",
                "Приводит переданный текст к верхнему регистру.",
                objectSchema(List.of(
                        new Param("text", "string", "Исходный текст")), List.of("text")),
                args -> {
                    String text = stringArg(args, "text");
                    return text.toUpperCase();
                }));
    }

    private static int intArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Отсутствует аргумент: " + name);
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Аргумент '" + name + "' должен быть целым числом: " + value);
        }
    }

    private static String stringArg(Map<String, Object> args, String name) {
        Object value = args.get(name);
        if (value == null) {
            throw new IllegalArgumentException("Отсутствует аргумент: " + name);
        }
        return String.valueOf(value);
    }

    private JsonNode objectSchema(List<Param> params, List<String> required) {
        ObjectNode properties = mapper.createObjectNode();
        for (Param param : params) {
            properties.set(param.name(), mapper.createObjectNode()
                    .put("type", param.type())
                    .put("description", param.description()));
        }
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        schema.set("properties", properties);
        schema.set("required", mapper.valueToTree(required));
        return schema;
    }

    private record Param(String name, String type, String description) {
    }
}