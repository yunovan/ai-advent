package com.yunovan.aiadvent.day29;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.yunovan.aiadvent.day21.Day21CorpusLoader;
import com.yunovan.aiadvent.day21.Day21EmbeddingService;
import com.yunovan.aiadvent.day21.Day21FixedChunker;
import com.yunovan.aiadvent.day21.Day21IndexFacade;
import com.yunovan.aiadvent.day21.Day21IndexStore;
import com.yunovan.aiadvent.day21.Day21Properties;
import com.yunovan.aiadvent.day21.Day21StructuralChunker;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import com.yunovan.aiadvent.day28.Day28Properties;
import com.yunovan.aiadvent.day28.Day28RagService;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

class Day29LocalLlmRealTest {

    private static final String EMBEDDINGS_QUESTION =
            "Что такое эмбеддинги документов и как они получаются?";

    @TempDir
    Path tempDir;

    private Day29OptimizationService service;
    private Day29ModelReport report;

    @BeforeEach
    void setUp() {
        String endpoint = System.getenv().getOrDefault("DAY26_ENDPOINT",
                Day26Properties.DEFAULT_ENDPOINT);
        Day26Properties localProperties = new Day26Properties(endpoint, null, null, null, null, null);
        Day21Properties indexProperties = new Day21Properties(
                0, "/mcp", "test", "0.0.1",
                tempDir.resolve("store").toString(), tempDir.resolve("corpus").toString(),
                600, 80, 512);
        Day21IndexFacade facade = new Day21IndexFacade(
                new Day21CorpusLoader(indexProperties),
                new Day21EmbeddingService(indexProperties),
                new Day21IndexStore(indexProperties), indexProperties,
                List.of(new Day21FixedChunker(indexProperties),
                        new Day21StructuralChunker(indexProperties)));
        Day26LocalLlmClient localLlm = new Day26LocalLlmClient(
                RestClient.builder(), localProperties);
        Day28RagService rag = new Day28RagService(facade,
                new Day28Properties(null, null, null, null, null, null, null),
                localProperties, localLlm, mock(LlmClient.class),
                new LlmProperties(null, null, null));
        service = new Day29OptimizationService(rag, localLlm, localProperties,
                new Day29Properties(null, null, null, null, null));

        Day29HealthResponse health = service.health();
        Assumptions.assumeTrue(health.model().available(),
                "Ollama не запущена на " + endpoint + " — реальный тест пропущен");
        Assumptions.assumeTrue(health.model().installed(),
                "модель " + health.model().model() + " не установлена — реальный тест пропущен");
        report = health.model();
    }

    @Test
    void healthDetectsQuantizationContextWindowAndMemory() {
        assertThat(report.format()).isEqualTo("gguf");
        assertThat(report.quantizationLevel()).isNotBlank();
        assertThat(report.parameterSize()).isNotBlank();
        assertThat(report.parameterCount()).isPositive();
        assertThat(report.contextLength()).isPositive();
        assertThat(report.modelSizeBytes()).isPositive();
        assertThat(report.loadedMemoryBytes()).isNotNegative();
        assertThat(report.reason()).isEmpty();
    }

    @Test
    void askAnswersWithBothProfilesAndShrinksPrompt() {
        Day29AskResponse response = service.ask(EMBEDDINGS_QUESTION);

        assertThat(response.sources()).contains("embeddings.md");
        assertThat(response.promptCharsTuned())
                .isPositive()
                .isLessThan(response.promptCharsBaseline());
        assertThat(response.baseline().fallback()).isFalse();
        assertThat(response.tuned().fallback()).isFalse();
        assertThat(response.baseline().answer()).isNotBlank();
        assertThat(response.tuned().answer()).isNotBlank();
        assertThat(response.baseline().latencyMs()).isPositive();
        assertThat(response.tuned().latencyMs()).isPositive();
        assertThat(response.baseline().promptTokens()).isPositive();
        assertThat(response.tuned().promptTokens()).isPositive();
        assertThat(response.baseline().groundingPercent()).isNotNull();
        assertThat(response.tuned().groundingPercent()).isNotNull();
        assertThat(response.qualityVerdict()).contains("Качество");
        assertThat(response.speedVerdict()).contains("Скорость");
    }
}
