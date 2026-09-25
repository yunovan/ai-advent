package com.yunovan.aiadvent.day18;

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
public class Day18McpServer {

    public static final String PROTOCOL_VERSION = "2024-11-05";
    public static final String SESSION_HEADER = "Mcp-Session-Id";

    private static final Logger log = LoggerFactory.getLogger(Day18McpServer.class);
    private static final int JSONRPC_PARSE_ERROR = -32700;
    private static final int JSONRPC_INVALID_REQUEST = -32600;
    private static final int JSONRPC_METHOD_NOT_FOUND = -32601;
    private static final int JSONRPC_INVALID_PARAMS = -32602;
    private static final int JSONRPC_SERVER_ERROR = -32000;

    private final Day18Properties properties;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Day18Tool> tools = new LinkedHashMap<>();
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    private HttpServer server;
    private ExecutorService executor;
    private int boundPort;

    public Day18McpServer(Day18Properties properties, Day18SchedulerApi api) {
        this.properties = properties;
        registerTools(api);
    }

    private void registerTools(Day18SchedulerApi api) {
        tools.put("scheduler_add_reminder", new Day18Tool(
                "scheduler_add_reminder",
                "Ставит напоминание: задание выполнится один раз через delaySeconds секунд. "
                        + "Возвращает созданное задание планировщика.",
                objectSchema(List.of(
                        new Param("topic", "string", "Текст напоминания"),
                        new Param("delaySeconds", "integer",
                                "Через сколько секунд напомнить (обязательный)")),
                        List.of("delaySeconds")),
                args -> mapper.valueToTree(api.addReminder(
                        stringArgOrNull(args, "topic"), intArg(args, "delaySeconds"))).toString()));

        tools.put("scheduler_add_collector", new Day18Tool(
                "scheduler_add_collector",
                "Запускает периодический сбор данных каждые periodSeconds секунд. "
                        + "Каждый запуск сохраняет замер: если указан url — проверка доступности сайта "
                        + "(пинг), если sourceFeed — регулярная сводка по ранее собранным данным. "
                        + "Возвращает созданное задание планировщика.",
                objectSchema(List.of(
                        new Param("feed", "string",
                                "Имя потока данных, куда пишутся замеры (обязательный)"),
                        new Param("periodSeconds", "integer",
                                "Период сбора в секундах (обязательный)"),
                        new Param("url", "string",
                                "URL для проверки доступности (необязательно)"),
                        new Param("sourceFeed", "string",
                                "Поток, по которому писать регулярную сводку (необязательно)")),
                        List.of("feed", "periodSeconds")),
                args -> mapper.valueToTree(api.addCollector(
                        stringArg(args, "feed"), intArg(args, "periodSeconds"),
                        stringArgOrNull(args, "url"), stringArgOrNull(args, "sourceFeed"))).toString()));

        tools.put("scheduler_list_jobs", new Day18Tool(
                "scheduler_list_jobs",
                "Возвращает список заданий планировщика со статусом, параметрами и временем "
                        + "следующего запуска.",
                objectSchema(List.of(), List.of()),
                args -> mapper.valueToTree(api.listJobs()).toString()));

        tools.put("scheduler_summary", new Day18Tool(
                "scheduler_summary",
                "Возвращает агрегированный результат по собранным данным: сколько событий, когда первое "
                        + "и последнее, среднее/мин/макс значение, долю успешных проверок и последние записи.",
                objectSchema(List.of(
                        new Param("feed", "string",
                                "Поток данных; если не указан — сводка по всем"),
                        new Param("sinceSeconds", "integer",
                                "Смотреть только события за последние N секунд")),
                        List.of()),
                args -> mapper.valueToTree(api.summary(
                        stringArgOrNull(args, "feed"), optionalIntArg(args, "sinceSeconds"))).toString()));

        tools.put("scheduler_run_now", new Day18Tool(
                "scheduler_run_now",
                "Немедленно выполняет задание планировщика по id и возвращает обновлённое задание. "
                        + "Нужно для демонстрации без ожидания расписания.",
                objectSchema(List.of(
                        new Param("jobId", "string", "Идентификатор задания планировщика (обязательный)")),
                        List.of("jobId")),
                args -> mapper.valueToTree(api.runNow(stringArg(args, "jobId"))).toString()));

        tools.put("scheduler_stop_process", new Day18Tool(
                "scheduler_stop_process",
                "Останавливает процесс: если указан jobId — конкретное задание планировщика, "
                        + "если не указан — все активные процессы сбора и ожидающие напоминания. "
                        + "Возвращает список остановленных заданий.",
                objectSchema(List.of(
                        new Param("jobId", "string",
                                "Идентификатор задания; если не указан — остановить все процессы")),
                        List.of()),
                args -> mapper.valueToTree(api.stopProcess(
                        stringArgOrNull(args, "jobId"))).toString()));
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
            log.info("День 18: MCP-сервер планировщика запущен: http://localhost:{}{}", boundPort, properties.path());
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

    public Map<String, Day18Tool> toolMap() {
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
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, " + SESSION_HEADER);
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
        for (Day18Tool tool : tools.values()) {
            ObjectNode toolNode = toolsNode.addObject();
            toolNode.put("name", tool.name());
            toolNode.put("description", tool.description());
            toolNode.set("inputSchema", tool.inputSchema());
        }
        return wrapResult(id, result);
    }

    private JsonNode toolsCall(String id, JsonNode params) {
        String toolName = params.get("name") == null ? "" : params.get("name").asText();
        Day18Tool tool = tools.get(toolName);
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

    private static int intArg(Map<String, Object> args, String key) {
        Object value = args.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Укажите целое число " + key);
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException("Укажите целое число " + key + " больше нуля");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Укажите целое число " + key);
        }
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