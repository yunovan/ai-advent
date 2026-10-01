package com.yunovan.aiadvent.day21;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day21McpServerTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path tempDir;

    private Day21McpServer server;
    private HttpClient http;
    private String url;

    @BeforeEach
    void setUp() {
        Day21IndexFacade facade = mock(Day21IndexFacade.class);
        when(facade.health()).thenReturn(new Day21HealthResponse(
                "ai-advent-index-mcp", "0.1.0", 9, 131000, 44,
                List.of(new Day21StrategyInfo("fixed", "описание", true, 123),
                        new Day21StrategyInfo("structural", "описание", true, 98))));
        when(facade.ingest(any())).thenReturn(new Day21IngestResponse(
                "fixed", 9, 123, 131000,
                tempDir.resolve("index-fixed.json").toString()));
        Day21Chunk chunk = new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                "Эмбеддинги", "Метрики близости", "articles/embeddings.md#fixed#0001",
                0, 100, "Текст про локальные эмбеддинги и хэш-признаки.");
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse(
                "fixed", "n грамм", 3,
                List.of(new Day21SearchHit(chunk, 0.91, "признаковые эмбеддинги"))));
        Day21StrategyMetric fixed = new Day21StrategyMetric(
                "fixed", 9, 123, 131000, 520.0, 300.0, 900.0, 140.0, 0.27, 5, 6, 83.3);
        Day21StrategyMetric structural = new Day21StrategyMetric(
                "structural", 9, 98, 131000, 620.0, 400.0, 850.0, 90.0, 0.15, 6, 6, 100.0);
        when(facade.compare()).thenReturn(new Day21ComparisonResponse(
                List.of(fixed, structural), List.of("запрос"), "Лучше structural"));
        when(facade.chunks(any())).thenReturn(List.of(chunk));

        server = new Day21McpServer(new Day21Properties(
                0, "/mcp", "ai-advent-index-mcp", "0.1.0", tempDir.toString(), tempDir.toString(),
                null, null, null), facade);
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
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        if (sessionId != null) {
            builder.header(Day21McpServer.SESSION_HEADER, sessionId);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private String sessionId() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\"}}", null);
        assertThat(response.statusCode()).isEqualTo(200);
        return response.headers().firstValue(Day21McpServer.SESSION_HEADER).orElse(null);
    }

    private String callToolText(String tool, String arguments, String session) throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"" + tool + "\",\"arguments\":" + arguments + "}}", session);
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = mapper.readTree(response.body());
        if (json.has("error")) {
            throw new AssertionError("tools/call вернул ошибку: " + json.get("error").get("message").asText());
        }
        return json.path("result").path("content").get(0).path("text").asText();
    }

    @Test
    void toolsListReturnsTheFiveIndexTools() throws Exception {
        String session = sessionId();
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}",
                session);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = mapper.readTree(response.body());
        assertThat(json.path("result").path("tools")).hasSize(5);
        assertThat(json.path("result").path("tools").get(0).path("name").asText())
                .isEqualTo("index_status");
        assertThat(json.path("result").path("tools").get(4).path("name").asText())
                .isEqualTo("index_chunks");
    }

    @Test
    void indexStatusReportsCorpusSize() throws Exception {
        String session = sessionId();
        String text = callToolText("index_status", "{}", session);

        assertThat(text)
                .contains("ai-advent-index-mcp")
                .contains("131000")
                .contains("fixed");
    }

    @Test
    void indexIngestBuildsIndexText() throws Exception {
        String session = sessionId();
        String text = callToolText("index_ingest", "{\"strategy\":\"fixed\"}", session);

        assertThat(text).contains("построен").contains("чанков");
        assertThat(text).contains("index-fixed.json");
    }

    @Test
    void indexSearchReturnsTopHitWithMetadata() throws Exception {
        String session = sessionId();
        String text = callToolText("index_search", "{\"query\":\"n грамм\"}", session);

        assertThat(text).contains("0.910").contains("embeddings.md").contains("Эмбеддинги");
    }

    @Test
    void indexCompareReturnsVerdict() throws Exception {
        String session = sessionId();
        String text = callToolText("index_compare", "{}", session);

        assertThat(text).contains("Вердикт").contains("Лучше structural");
    }

    @Test
    void indexChunksHonoursLimit() throws Exception {
        String session = sessionId();
        String text = callToolText("index_chunks", "{\"limit\":5}", session);

        assertThat(text).contains("Чанков: 1").contains("articles/embeddings.md#fixed#0001");
    }

    @Test
    void searchWithoutSessionIsRejected() throws Exception {
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"index_search\",\"arguments\":{\"query\":\"текст\"}}}", null);

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode json = mapper.readTree(response.body());
        assertThat(json.path("error").path("code").asInt()).isEqualTo(-32600);
    }

    @Test
    void unknownToolFailsWithMethodNotFound() throws Exception {
        String session = sessionId();
        HttpResponse<String> response = post("{\"jsonrpc\":\"2.0\",\"id\":9,\"method\":\"tools/call\","
                + "\"params\":{\"name\":\"calculator\"}}", session);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode json = mapper.readTree(response.body());
        assertThat(json.path("error").path("code").asInt()).isEqualTo(-32601);
        assertThat(json.path("error").path("message").asText()).contains("Инструмент не найден");
    }
}