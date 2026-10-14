package com.yunovan.aiadvent.day33;

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

class Day33LocalLlmRealTest {

    private Day33McpServer server;
    private Day33SupportService service;
    private Day33HealthResponse health;

    @BeforeEach
    void setUp() throws IOException {
        int port = freePort();
        Day26Properties llmProperties = new Day26Properties(
                System.getenv().getOrDefault("DAY26_ENDPOINT", Day26Properties.DEFAULT_ENDPOINT),
                null, null, null, null, null);
        Day33Properties properties = new Day33Properties(port, "/mcp", null, null,
                "data/day33-none", "build/day33-none", 5, 12, 700, 80, 400, 0.2);
        Day33CrmStore crm = new Day33CrmStore(properties);
        server = new Day33McpServer(properties, crm);
        server.start();
        Day33McpClient mcp = new Day33McpClient(properties);
        Day21EmbeddingService embeddings = new Day21EmbeddingService(
                new Day21Properties(null, null, null, null, null, null, null, null, null));
        Day33SupportIndex index = new Day33SupportIndex(properties, embeddings);
        Day26LocalLlmClient llm = new Day26LocalLlmClient(RestClient.builder(), llmProperties);
        service = new Day33SupportService(properties, index, crm, mcp, llm, llmProperties);

        health = service.health();
        Assumptions.assumeTrue(health.model().available(),
                "Ollama не доступна на " + llmProperties.endpoint() + " - режим без прогонов");
        Assumptions.assumeTrue(health.model().installed(),
                "Модель " + health.model().model() + " не установлена - режим без прогонов");
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
        assertThat(health.documents()).contains("authorization.md", "faq.md");
        assertThat(health.chunks()).isPositive();
        assertThat(health.users()).isEqualTo(4);
        assertThat(health.tickets()).isEqualTo(5);
        assertThat(health.mcp().available()).isTrue();
        assertThat(health.mcp().tools()).isEqualTo(5);
    }

    @Test
    void answersSupportQuestionWithTicketContext() {
        Day33SupportResponse response = service.ask(
                "Почему не работает авторизация?", "T-1001", null);

        assertThat(response.answer()).isNotBlank();
        assertThat(response.ticketId()).isEqualTo("T-1001");
        assertThat(response.userId()).isEqualTo("U-1001");
        assertThat(response.mcpUsed()).isTrue();
        assertThat(response.retrieved()).isPositive();
        assertThat(response.sourceFiles()).isNotEmpty();
    }

    @Test
    void ticketToolRunsThroughMcp() {
        Day33CrmResponse response = service.crm("crm_ticket", Map.of("ticketId", "T-1001"));

        assertThat(response.ok()).isTrue();
        assertThat(response.content()).contains("Не работает авторизация");
    }
}
