package com.yunovan.aiadvent.day32;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.day21.Day21EmbeddingService;
import com.yunovan.aiadvent.day21.Day21Properties;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class Day32LocalLlmRealTest {

    private static final String RISKY_DIFF = """
            diff --git a/src/main/java/Foo.java b/src/main/java/Foo.java
            --- a/src/main/java/Foo.java
            +++ b/src/main/java/Foo.java
            @@ -1,0 +1,2 @@
            +String apiKey = "sk-abcdef123456";
            +System.out.println("debug");
            """;

    private Day32ReviewService service;
    private Day32HealthResponse health;

    @BeforeEach
    void setUp() {
        Day26Properties llmProperties = new Day26Properties(
                System.getenv().getOrDefault("DAY26_ENDPOINT", Day26Properties.DEFAULT_ENDPOINT),
                null, null, null, null, null);
        Day32Properties properties = new Day32Properties(".", "main", "HEAD", "docs", "README.md",
                "src/main/java/com/yunovan/aiadvent/day32", 6, 12, 700, 80, 5_000, 14_000, 100, 700, 0.1);
        Day21EmbeddingService embeddings = new Day21EmbeddingService(
                new Day21Properties(null, null, null, null, null, null, null, null, null));
        Day32ReviewIndex index = new Day32ReviewIndex(properties, embeddings);
        Day32DiffSource git = new Day32DiffSource(properties);
        Day26LocalLlmClient llm = new Day26LocalLlmClient(RestClient.builder(), llmProperties);
        service = new Day32ReviewService(properties, index, git, llm, llmProperties);

        health = service.health();
        Assumptions.assumeTrue(health.model().available(),
                "Ollama не запущена на " + llmProperties.endpoint() + " — реальный тест пропущен");
        Assumptions.assumeTrue(health.model().installed(),
                "модель " + health.model().model() + " не установлена — реальный тест пропущен");
    }

    @Test
    void healthReportsReadyReview() {
        assertThat(health.status()).isEqualTo("ok");
        assertThat(health.branch()).isNotBlank();
        assertThat(health.chunks()).isPositive();
        assertThat(health.codeFiles()).isPositive();
        assertThat(health.model().installed()).isTrue();
    }

    @Test
    void reviewProducesSectionsWithRealModel() {
        Day32ReviewResponse response = service.review(new Day32ReviewRequest("main", "HEAD",
                RISKY_DIFF, "Реальный тест ревью", "тестовое описание"));

        assertThat(response.fallback()).isFalse();
        assertThat(response.review()).isNotBlank();
        assertThat(response.summary()).contains("моделью");
        assertThat(response.bugs()).isNotEmpty();
        assertThat(response.latencyMs()).isPositive();
    }
}
