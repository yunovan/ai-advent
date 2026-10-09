package com.yunovan.aiadvent.day26;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

public final class Day26MockOllama implements AutoCloseable {

    public static final String VERSION = "0.35.1";
    public static final String MODEL_TAG = "qwen2.5:3b";
    public static final String CYRILLIC_REPLY = "Я Qwen, языковая модель от Alibaba Cloud.";

    private final HttpServer server;
    private final AtomicInteger chatRequests = new AtomicInteger();

    private volatile String tagsJson = """
            {"models":[{"name":"%s","size":1998578976,
            "details":{"parameter_size":"3.09B","quantization_level":"Q4_K_M"}}]}
            """.formatted(MODEL_TAG);
    private volatile String chatJson = """
            {"model":"%s","done":true,
            "message":{"role":"assistant","content":"%s"},
            "eval_count":25,"prompt_eval_count":45,
            "eval_duration":4700000000,"prompt_eval_duration":200000000,
            "load_duration":100000000,"total_duration":5000000000}
            """.formatted(MODEL_TAG, CYRILLIC_REPLY);
    private volatile String psJson = """
            {"models":[{"name":"%s","size":2047774554,"size_vram":0,
            "expires_at":"2026-10-08T14:35:58.582395+03:00"}]}
            """.formatted(MODEL_TAG);
    private volatile String showJson = """
            {"details":{"format":"gguf","family":"qwen2","families":["qwen2"],
            "parameter_size":"3.1B","quantization_level":"Q4_K_M","parent_model":"","license":"qwen-research"},
            "model_info":{"general.architecture":"qwen2","general.parameter_count":3085938688,
            "qwen2.context_length":32768}}
            """;
    private volatile int chatStatus = 200;
    private volatile String lastChatBody = "";

    private Day26MockOllama(HttpServer server) {
        this.server = server;
    }

    public static Day26MockOllama start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            Day26MockOllama mock = new Day26MockOllama(server);
            server.createContext("/api/version", mock::version);
            server.createContext("/api/tags", mock::tags);
            server.createContext("/api/chat", mock::chat);
            server.createContext("/api/show", mock::show);
            server.createContext("/api/ps", mock::ps);
            server.start();
            return mock;
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось поднять mock-сервер Ollama", ex);
        }
    }

    public static String closedEndpoint() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return "http://127.0.0.1:" + socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось получить свободный порт", ex);
        }
    }

    public String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void chatJson(String json) {
        this.chatJson = json;
    }

    public void chatStatus(int status) {
        this.chatStatus = status;
    }

    public void tagsJson(String json) {
        this.tagsJson = json;
    }

    public void showJson(String json) {
        this.showJson = json;
    }

    public void psJson(String json) {
        this.psJson = json;
    }

    public int chatRequests() {
        return chatRequests.get();
    }

    public String lastChatBody() {
        return lastChatBody;
    }

    private void version(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "{\"version\":\"" + VERSION + "\"}");
    }

    private void tags(HttpExchange exchange) throws IOException {
        respond(exchange, 200, tagsJson);
    }

    private void chat(HttpExchange exchange) throws IOException {
        chatRequests.incrementAndGet();
        lastChatBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        respond(exchange, chatStatus, chatJson);
    }

    private void show(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        respond(exchange, 200, showJson);
    }

    private void ps(HttpExchange exchange) throws IOException {
        respond(exchange, 200, psJson);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
