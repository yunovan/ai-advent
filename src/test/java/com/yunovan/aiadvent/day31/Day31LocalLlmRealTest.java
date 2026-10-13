package com.yunovan.aiadvent.day31;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.day21.Day21EmbeddingService;
import com.yunovan.aiadvent.day21.Day21Properties;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class Day31LocalLlmRealTest {

    private Day31McpServer server;
    private Day31DeveloperAssistantService service;
    private Day31HealthResponse health;

    @BeforeEach
    void setUp() throws IOException {
        int port = freePort();
        Day26Properties llmProperties = new Day26Properties(
                System.getenv().getOrDefault("DAY26_ENDPOINT", Day26Properties.DEFAULT_ENDPOINT),
                null, null, null, null, null);
        Day31Properties properties = new Day31Properties(port, "/mcp", null, null, ".", "docs",
                "README.md", 5, 12, 700, 80, 5_000, 400, 300, 6_000, 0.2);
        Day31GitTool git = new Day31GitTool(properties);
        server = new Day31McpServer(properties, git);
        server.start();
        Day31McpClient mcp = new Day31McpClient(properties);
        Day21EmbeddingService embeddings = new Day21EmbeddingService(
                new Day21Properties(null, null, null, null, null, null, null, null, null));
        Day31ProjectIndex index = new Day31ProjectIndex(properties, embeddings);
        Day26LocalLlmClient llm = new Day26LocalLlmClient(RestClient.builder(), llmProperties);
        service = new Day31DeveloperAssistantService(properties, index, mcp, git, llm, llmProperties);

        health = service.health();
        Assumptions.assumeTrue(health.model().available(),
                "Ollama не запущена на " + llmProperties.endpoint() + " — реальный тест пропущен");
        Assumptions.assumeTrue(health.model().installed(),
                "модель " + health.model().model() + " не установлена — реальный тест пропущен");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void healthReportsReadyAssistant() {
        assertThat(health.status()).isEqualTo("ok");
        assertThat(health.branch()).isNotBlank();
        assertThat(health.documents()).contains("README.md", "architecture.md");
        assertThat(health.chunks()).isPositive();
        assertThat(health.mcp().available()).isTrue();
        assertThat(health.mcp().tools()).isEqualTo(5);
    }

    @Test
    void helpAnswersProjectQuestionFromDocsAndGit() {
        Day31HelpResponse response = service.help(
                "Как устроен RAG в проекте и где хранится индекс документов?");

        assertThat(response.answer()).isNotBlank();
        assertThat(response.mcpUsed()).isTrue();
        assertThat(response.gitBranch()).isNotBlank();
        assertThat(response.retrieved()).isPositive();
        assertThat(response.sourceFiles()).isNotEmpty();
    }

    @Test
    void gitBranchToolRunsThroughMcp() {
        Day31GitResponse response = service.git("git_branch", Map.of());

        assertThat(response.ok()).isTrue();
        assertThat(response.content()).contains("Ветка:");
    }
}
