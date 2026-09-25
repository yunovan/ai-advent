package com.yunovan.aiadvent.day19;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Day19McpServer {

    public static final String PROTOCOL_VERSION = "2024-11-05";
    public static final String SESSION_HEADER = "Mcp-Session-Id";

    private static final Logger log = LoggerFactory.getLogger(Day19McpServer.class);
    private static final int JSONRPC_PARSE_ERROR = -32700;
    private static final int JSONRPC_INVALID_REQUEST = -32600;
    private static final int JSONRPC_METHOD_NOT_FOUND = -32601;
    private static final int JSONRPC_INVALID_PARAMS = -32602;
    private static final int JSONRPC_SERVER_ERROR = -32000;

    private final Day19Properties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Day19Tool> tools = new LinkedHashMap<>();
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    private HttpServer server;
    private ExecutorService executor;
    private int boundPort;

    public Day19McpServer(Day19Properties properties, Day19MarketApi api) {
        this.properties = properties;
        registerTools(api);
    }

    private void registerTools(Day19MarketApi api) {
        tools.put("search", new Day19Tool(
                "search",
                "Ищет товары в каталоге магазинов. Параметры: query — поисковый запрос, category — "
                        + "категория (ноутбуки, смартфоны, телевизоры, наушники), maxResults — лимит, "
                        + "sort — сортировка (price_asc, price_desc, rating). Возвращает JSON-список товаров "
                        + "с ценой, рейтингом и ссылкой на сайт продавца.",
                objectSchema(List.of(
                        new Param("query", "string", "Поисковый запрос (обязательный)"),
                        new Param("category", "string", "Категория товара"),
                        new Param("maxResults", "integer", "Максимум результатов"),
                        new Param("sort", "string", "Сортировка: price_asc, price_desc, rating")),
                        List.of("query")),
                args -> {
                    String data = mapper.createObjectNode().putPOJO("products", api.search(
                            stringArg(args, "query"), stringArgOrNull(args, "category"),
                            optionalIntArg(args, "maxResults"), stringArgOrNull(args, "sort"))).toString();
                    return data;
                }));

        tools.put("summarize", new Day19Tool(
                "summarize",
                "Строит сводную таблицу сравнения по результату инструмента search: ключевые параметры "
                        + "и ссылки на сайты продавцов. Параметры: query — запрос, data — JSON-список "
                        + "товаров, который вернул search (обязательный), format — markdown или csv.",
                objectSchema(List.of(
                        new Param("query", "string", "Запрос для заголовка таблицы"),
                        new Param("data", "string", "JSON-результат скоу search (обязательный)"),
                        new Param("format", "string", "Формат таблицы: markdown или csv")),
                        List.of("data")),
                args -> api.summarize(
                        stringArg(args, "query"), stringArg(args, "data"),
                        stringArgOrNull(args, "format"))));

        tools.put("saveToFile", new Day19Tool(
                "saveToFile",
                "Сохраняет сводную таблицу в файл. Параметры: summary — текст таблицы из summarize "
                        + "(обязательный), data — JSON-результат search (нужен для csv и json), "
                        + "format — markdown, csv, json или txt, fileName — имя файла. Возвращает путь "
                        + "и размер сохранённого файла.",
                objectSchema(List.of(
                        new Param("summary", "string", "Текст сводной таблицы из summarize (обязательный)"),
                        new Param("data", "string", "JSON-результат search"),
                        new Param("format", "string", "markdown, csv, json или txt"),
                        new Param("fileName", "string", "Имя файла без расширения")),
                        List.of("summary")),
                args -> mapper.valueToTree(api.saveFile(
                        stringArgOrNull(args, "data"), stringArg(args, "summary"),
                        stringArgOrNull(args, "format"), stringArgOrNull(args, "fileName"))).toString()));
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
            log.info("День 19: MCP-сервер пайплайна запущен: http://localhost:{}{}", boundPort, properties.path());
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось запустить MCP-сервер на порту " + properties.serverPort(), ex);
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

    public Map<String, Day19Tool> toolMap() {
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
            MpcResponse response = dispatch(exchange.getRequestHeaders().getFirst(SESSION_HEADER), body);
            JsonNode responseBody = response.body();
            if (responseBody == null) {
                responseBody = errorNode(null, JSONRPC_INVALID_REQUEST, "Пустой ответ сервера");
            }
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

    private MpcResponse dispatch(String sessionHeader, String body) {
        JsonNode json;
        try {
            json = mapper.readTree(body);
        } catch (IOException ex) {
            return MpcResponse.of(errorNode(null, JSONRPC_PARSE_ERROR, "Не удалось разобрать JSON: " + ex.getMessage()));
        }
        String id = json.get("id") == null ? null : json.get("id").asText();
        String method = json.get("method") == null ? null : json.get("method").asText();
        JsonNode params = json.get("params");
        if (params == null || params.isNull()) {
            params = mapper.createObjectNode();
        }
        if (method == null) {
            return MpcResponse.of(errorNode(id, JSONRPC_INVALID_REQUEST, "Запрос должен содержать method"));
        }
        switch (method) {
            case "initialize":
                String session = sessionHeader == null || sessionHeader.isBlank()
                        ? UUID.randomUUID().toString() : sessionHeader;
                sessions.add(session);
                return MpcResponse.of(initializeResult(id, session))
                        .withHeader(SESSION_HEADER, session);
            case "notifications/initialized":
                return MpcResponse.of(okNode(id));
            case "tools/list":
                if (!hasSession(sessionHeader)) {
                    return MpcResponse.of(errorNode(id, JSONRPC_INVALID_REQUEST,
                            "Сначала установите сессию: вызовите initialize")).withStatus(400);
                }
                return MpcResponse.of(toolsList(id, params));
            case "tools/call":
                if (!hasSession(sessionHeader)) {
                    return MpcResponse.of(errorNode(id, JSONRPC_INVALID_REQUEST,
                            "Сначала установите сессию: вызовите initialize")).withStatus(400);
                }
                return MpcResponse.of(toolsCall(id, params));
            default:
                return MpcResponse.of(errorNode(id, JSONRPC_METHOD_NOT_FOUND, "Метод не найден: " + method));
        }
    }

    private boolean hasSession(String sessionHeader) {
        return sessionHeader != null && sessions.contains(sessionHeader);
    }

    private JsonNode initializeResult(String id, String session) {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", properties.name());
        serverInfo.put("version", properties.version());
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("result", result);
        putId(response, id);
        return response;
    }

    private JsonNode toolsList(String id, JsonNode params) {
        ObjectNode result = mapper.createObjectNode();
        ArrayNode toolsNode = result.putArray("tools");
        for (Day19Tool tool : tools.values()) {
            ObjectNode toolNode = toolsNode.addObject();
            toolNode.put("name", tool.name());
            toolNode.put("description", tool.description());
            toolNode.set("inputSchema", tool.inputSchema());
        }
        return wrapResult(id, result);
    }

    private JsonNode toolsCall(String id, JsonNode params) {
        String toolName = params.get("name") == null ? "" : params.get("name").asText();
        Day19Tool tool = tools.get(toolName);
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
        ArrayNode content = result.putArray("content");
        ObjectNode textBox = content.addObject();
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
        if (node == null) {
            return Map.of();
        }
        if (node.isObject() && node.has("values")) {
            node = node.get("values");
        }
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(), scalarValue(e.getValue())));
        return map;
    }

    private static Object scalarValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.toString();
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private static String stringArgOrNull(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer optionalIntArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Укажите целое число " + key);
        }
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

    private record Param(String name, String type, String description) {
    }

    private record MpcResponse(JsonNode body, int status, Map<String, String> headers) {

        static MpcResponse of(JsonNode body) {
            return new MpcResponse(body, 200, Map.of());
        }

        MpcResponse withStatus(int newStatus) {
            return new MpcResponse(body, newStatus, headers);
        }

        MpcResponse withHeader(String key, String value) {
            Map<String, String> merged = new HashMap<>(headers);
            merged.put(key, value);
            return new MpcResponse(body, status, merged);
        }
    }
}