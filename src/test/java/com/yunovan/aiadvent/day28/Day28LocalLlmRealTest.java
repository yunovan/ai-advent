package com.yunovan.aiadvent.day28;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.yunovan.aiadvent.day21.Day21CorpusLoader;
import com.yunovan.aiadvent.day21.Day21EmbeddingService;
import com.yunovan.aiadvent.day21.Day21FixedChunker;
import com.yunovan.aiadvent.day21.Day21IndexFacade;
import com.yunovan.aiadvent.day21.Day21IndexStore;
import com.yunovan.aiadvent.day21.Day21Properties;
import com.yunovan.aiadvent.day21.Day21StructuralChunker;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

class Day28LocalLlmRealTest {

    private static final String EMBEDDINGS_QUESTION =
            "Что такое эмбеддинги документов и как они получаются?";

    @TempDir
    Path tempDir;

    private Day28RagService service;
    private LlmClient cloud;

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
        cloud = mock(LlmClient.class);
        service = new Day28RagService(facade,
                new Day28Properties(null, null, null, null, null, null, null),
                localProperties,
                new Day26LocalLlmClient(RestClient.builder(), localProperties),
                cloud, new LlmProperties(null, null, null));

        Day28HealthResponse health = service.health();
        Assumptions.assumeTrue(health.localAvailable(),
                "Ollama не запущена на " + endpoint + " — реальный тест пропущен");
        Assumptions.assumeTrue(health.modelInstalled(),
                "модель " + health.localModel() + " не установлена — реальный тест пропущен");
    }

    @Test
    void healthDetectsBundledIndexAndLocalServer() {
        Day28HealthResponse health = service.health();

        assertThat(health.documents()).isPositive();
        assertThat(health.corpusChars()).isPositive();
        assertThat(health.retrieval()).contains("локальный").contains("без сетевых вызовов");
        assertThat(health.strategy()).isEqualTo("fixed");
        assertThat(health.localAvailable()).isTrue();
        assertThat(health.localVersion()).isNotBlank();
        assertThat(health.modelInstalled()).isTrue();
        assertThat(health.cloudConfigured()).isFalse();
        assertThat(health.usesCloud()).isFalse();
        assertThat(health.localError()).isEmpty();
    }

    @Test
    void askAnswersFromRealLocalModelWithRetrievedSources() {
        Day28AnswerResponse answer = service.ask(EMBEDDINGS_QUESTION);

        assertThat(answer.engine()).isEqualTo(Day28RagService.ENGINE_LOCAL);
        assertThat(answer.fallback()).isFalse();
        assertThat(answer.unavailableReason()).isEmpty();
        assertThat(answer.answer()).isNotBlank();
        assertThat(answer.answer()).isNotEqualTo(EMBEDDINGS_QUESTION);
        assertThat(answer.sources()).contains("embeddings.md");
        assertThat(answer.retrievedHits()).isNotEmpty();
        assertThat(answer.groundingPercent()).isNotNull();
        assertThat(answer.matchedQuery()).contains("эмбеддинг").contains("вектор");
        assertThat(answer.promptTokens()).isPositive();
        assertThat(answer.outputTokens()).isPositive();
        assertThat(answer.latencyMs()).isPositive();
        verifyNoInteractions(cloud);
    }

    @Test
    void retrievalIsDeterministicAcrossRepeatedAsks() {
        Day28AnswerResponse first = service.ask(EMBEDDINGS_QUESTION);
        Day28AnswerResponse second = service.ask(EMBEDDINGS_QUESTION);

        assertThat(second.sources()).isEqualTo(first.sources());
        assertThat(second.matchedQuery()).isEqualTo(first.matchedQuery());
        assertThat(second.candidatesBefore()).isEqualTo(first.candidatesBefore());
        assertThat(second.filteredOut()).isEqualTo(first.filteredOut());
        assertThat(second.retrievedHits())
                .extracting(hit -> hit.chunk().chunkId())
                .isEqualTo(first.retrievedHits().stream()
                        .map(hit -> hit.chunk().chunkId()).toList());
        verifyNoInteractions(cloud);
    }
}
