package com.yunovan.aiadvent.day31;

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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Day31McpServer {

    public static final String PROTOCOL_VERSION = "2024-11-05";
    public static final String SESSION_HEADER = "Mcp-Session-Id";

    private static final Logger log = LoggerFactory.getLogger(Day31McpServer.class);
    private static final int JSONRPC_PARSE_ERROR = -32700;
    private static final int JSONRPC_INVALID_REQUEST = -32600;
    private static final int JSONRPC_METHOD_NOT_FOUND = -32601;
    private static final int JSONRPC_INVALID_PARAMS = -32602;
    private static final int JSONRPC_SERVER_ERROR = -32000;

    private final Day31Properties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Tool> tools = new LinkedHashMap<>();
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    private HttpServer server;
    private ExecutorService executor;
    private int boundPort;

    public Day31McpServer(Day31Properties properties, Day31GitTool git) {
        this.properties = properties;
        registerTools(git);
    }

    private void registerTools(Day31GitTool git) {
        tools.put("git_branch", new Tool(
                "git_branch",
                "Возвращает текущую git-ветку проекта, короткий хэш коммита и признак "
                        + "наличия незакоммиченных изменений. Это обязательный минимум "
                        + "подключения ассистента к проекту.",
                objectSchema(List.of(), List.of()),
                args -> {
                    Day31GitInfo info = git.info();
                    if (!info.available()) {
                        throw new IllegalStateException(info.reason());
                    }
                    return "Ветка: " + info.branch()
                            + "\nКоммит: " + info.commit()
                            + "\nНезакоммиченные изменения: " + (info.dirty() ? "есть" : "нет");
                }));

        tools.put("git_status", new Tool(
                "git_status",
                "Возвращает сводку git status проекта: изменённые и новые файлы, "
                        + "чистоту рабочего дерева.",
                objectSchema(List.of(), List.of()),
                args -> git.status()));

        tools.put("git_files", new Tool(
                "git_files",
                "Возвращает список файлов, отслеживаемых git. Можно задать префикс пути "
                        + "и лимит количества.",
                objectSchema(List.of(
                        new Param("prefix", "string", "Префикс пути, например src/main/java"),
                        new Param("limit", "integer", "Максимум файлов в ответе")),
                        List.of()),
                args -> git.files(stringArgOrNull(args, "prefix"), intArgOrNull(args, "limit"))));

        tools.put("git_diff", new Tool(
                "git_diff",
                "Возвращает diff проекта. По умолчанию — изменения рабочего дерева, "
                        + "с staged=true — проиндексированные. Длинный diff обрезается.",
                objectSchema(List.of(
                        new Param("staged", "boolean", "Показать проиндексированные изменения"),
                        new Param("maxChars", "integer", "Максимум символов в ответе")),
                        List.of()),
                args -> git.diff(boolArg(args, "staged"), intArgOrNull(args, "maxChars"))));

        tools.put("git_log", new Tool(
                "git_log",
                "Возвращает последние коммиты проекта в формате --oneline.",
                objectSchema(List.of(
                        new Param("limit", "integer", "Сколько коммитов вернуть (по умолчанию 10)")),
                        List.of()),
                args -> git.log(intArgOrNull(args, "limit"))));
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
            log.info("День 31: MCP-сервер проекта запущен: http://localhost:{}{}", boundPort, properties.path());
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Не удалось запустить MCP-сервер дня 31 на порту " + properties.serverPort(), ex);
        }
    }

    @PreDestroy
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public int boundPort() {
        return boundPort;
    }

    public Map<String, Tool> toolMap() {
        return Map.copyOf(tools);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, errorNode(null, JSONRPC_INVALID_REQUEST, "Метод не поддерживается"),
                        Map.of());
                return;
            }
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            RpcResponse response = dispatch(exchange.getRequestHeaders().getFirst(SESSION_HEADER), body);
            JsonNode responseBody = response.body() == null
                    ? errorNode(null, JSONRPC_INVALID_REQUEST, "Пустой ответ сервера") : response.body();
            respond(exchange, response.status(), responseBody, response.headers());
        } catch (IOException ex) {
            respond(exchange, 500, errorNode(null, JSONRPC_SERVER_ERROR, "Ошибка сервера: " + ex.getMessage()),
                    Map.of());
        }
    }

    private void respond(HttpExchange exchange, int status, JsonNode body, Map<String, String> extraHeaders)
            throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers",
                "Content-Type, Authorization, " + SESSION_HEADER);
        for (Map.Entry<String, String> header : extraHeaders.entrySet()) {
            exchange.getResponseHeaders().set(header.getKey(), header.getValue());
        }
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private RpcResponse dispatch(String sessionHeader, String body) {
        JsonNode json;
        try {
            json = mapper.readTree(body);
        } catch (IOException ex) {
            return RpcResponse.of(errorNode(null, JSONRPC_PARSE_ERROR,
                    "Не удалось разобрать JSON: " + ex.getMessage()));
        }
        String id = json.get("id") == null ? null : json.get("id").asText();
        String method = json.get("method") == null ? null : json.get("method").asText();
        JsonNode params = json.get("params");
        if (params == null || params.isNull()) {
            params = mapper.createObjectNode();
        }
        if (method == null) {
            return RpcResponse.of(errorNode(id, JSONRPC_INVALID_REQUEST, "Запрос должен содержать method"));
        }
        return switch (method) {
            case "initialize" -> {
                String session = sessionHeader == null || sessionHeader.isBlank()
                        ? UUID.randomUUID().toString() : sessionHeader;
                sessions.add(session);
                yield RpcResponse.of(initializeResult(id, session)).withHeader(SESSION_HEADER, session);
            }
            case "notifications/initialized" -> RpcResponse.of(okNode(id));
            case "tools/list" -> hasSession(sessionHeader)
                    ? RpcResponse.of(toolsList(id))
                    : RpcResponse.of(errorNode(id, JSONRPC_INVALID_REQUEST,
                            "Сначала установите сессию: вызовите initialize")).withStatus(400);
            case "tools/call" -> hasSession(sessionHeader)
                    ? RpcResponse.of(toolsCall(id, params))
                    : RpcResponse.of(errorNode(id, JSONRPC_INVALID_REQUEST,
                            "Сначала установите сессию: вызовите initialize")).withStatus(400);
            default -> RpcResponse.of(errorNode(id, JSONRPC_METHOD_NOT_FOUND, "Метод не найден: " + method));
        };
    }

    private boolean hasSession(String sessionHeader) {
        return sessionHeader != null && sessions.contains(sessionHeader);
    }

    private JsonNode initializeResult(String id, String session) {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        result.putObject("capabilities").putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", properties.name());
        serverInfo.put("version", properties.version());
        return wrapResult(id, result);
    }

    private JsonNode toolsList(String id) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode toolsNode = result.putArray("tools");
        for (Tool tool : tools.values()) {
            ObjectNode toolNode = toolsNode.addObject();
            toolNode.put("name", tool.name());
            toolNode.put("description", tool.description());
            toolNode.set("inputSchema", tool.inputSchema());
        }
        return wrapResult(id, result);
    }

    private JsonNode toolsCall(String id, JsonNode params) {
        String toolName = params.get("name") == null ? "" : params.get("name").asText();
        Tool tool = tools.get(toolName);
        if (tool == null) {
            return errorNode(id, JSONRPC_METHOD_NOT_FOUND, "Инструмент не найден: " + toolName);
        }
        Map<String, Object> args = simplifyArguments(params.get("arguments"));
        String text;
        try {
            text = tool.handler().apply(args);
        } catch (IllegalArgumentException ex) {
            return errorNode(id, JSONRPC_INVALID_PARAMS, ex.getMessage());
        } catch (RuntimeException ex) {
            return errorNode(id, JSONRPC_SERVER_ERROR, "Ошибка при вызове инструмента: " + ex.getMessage());
        }
        ObjectNode result = mapper.createObjectNode();
        ObjectNode textBox = result.putArray("content").addObject();
        textBox.put("type", "text");
        textBox.put("text", text);
        result.put("isError", false);
        return wrapResult(id, result);
    }

    private JsonNode wrapResult(String id, ObjectNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("result", result);
        putId(response, id);
        return response;
    }

    private JsonNode okNode(String id) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.putObject("result");
        putId(response, id);
        return response;
    }

    private JsonNode errorNode(String id, int code, String message) {
        ObjectNode root = mapper.createObjectNode();
        root.put("jsonrpc", "2.0");
        ObjectNode error = root.putObject("error");
        error.put("code", code);
        error.put("message", message);
        putId(root, id);
        return root;
    }

    private void putId(ObjectNode response, String id) {
        if (id == null) {
            response.putNull("id");
        } else {
            response.put("id", id);
        }
    }

    private static Map<String, Object> simplifyArguments(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> map.put(entry.getKey(), scalarValue(entry.getValue())));
        return map;
    }

    private static Object scalarValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isIntegralNumber()) {
            return node.longValue();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.toString();
    }

    private static String stringArgOrNull(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer intArgOrNull(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean boolArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value instanceof Boolean flag) {
            return flag;
        }
        return value instanceof String text && Boolean.parseBoolean(text.trim());
    }

    private ObjectNode objectSchema(List<Param> params, List<String> required) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");
        for (Param param : params) {
            ObjectNode prop = properties.putObject(param.name());
            prop.put("type", param.type());
            prop.put("description", param.description());
        }
        ArrayNode requiredNode = schema.putArray("required");
        for (String name : required) {
            requiredNode.add(name);
        }
        return schema;
    }

    public record Tool(String name, String description, ObjectNode inputSchema,
                       Function<Map<String, Object>, String> handler) {
    }

    private record Param(String name, String type, String description) {
    }

    private record RpcResponse(JsonNode body, int status, Map<String, String> headers) {

        static RpcResponse of(JsonNode body) {
            return new RpcResponse(body, 200, Map.of());
        }

        RpcResponse withStatus(int newStatus) {
            return new RpcResponse(body, newStatus, headers);
        }

        RpcResponse withHeader(String key, String value) {
            Map<String, String> merged = new HashMap<>(headers);
            merged.put(key, value);
            return new RpcResponse(body, status, merged);
        }
    }
}
