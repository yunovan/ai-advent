package com.yunovan.aiadvent.day21;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day21IndexPipelineTest {

    @TempDir
    Path tempDir;

    private Day21Properties properties;
    private Day21IndexFacade facade;

    @BeforeEach
    void setUp() throws Exception {
        Path corpus = tempDir.resolve("corpus");
        Files.createDirectories(corpus.resolve("articles"));
        Files.writeString(corpus.resolve("articles/custom.md"),
                "# Пользовательский корпус тестов\n\n"
                        + "Параграф про эмбеддинги и нарезку документов на чанки, достаточно длинный, "
                        + "чтобы получилось несколько чанков по стратегии и по размеру.\n\n"
                        + "# Раздел про память\n\n"
                        + "Здесь говорится про долговременную память агента и повторное использование "
                        + "контекста из предыдущих шагов.\n");
        properties = new Day21Properties(
                0, "/mcp", "test", "0.0.1",
                tempDir.resolve("store").toString(), corpus.toString(), 100, 20, 512);
        facade = buildFacade();
    }

    @Test
    void healthReportsCorpusAndStrategiesBeforeIngest() {
        Day21HealthResponse health = facade.health();

        assertThat(health.documents()).isEqualTo(2);
        assertThat(health.corpusChars()).isGreaterThan(100);
        assertThat(health.strategies()).hasSize(2);
        assertThat(health.strategies().get(0).name()).isEqualTo(Day21Properties.STRATEGY_FIXED);
        assertThat(health.strategies().get(1).name()).isEqualTo(Day21Properties.STRATEGY_STRUCTURAL);
        assertThat(health.strategies().get(0).indexed()).isFalse();
    }

    @Test
    void ingestBuildsPersistentJsonIndex() {
        Day21IngestResponse response = facade.ingest(Day21Properties.STRATEGY_FIXED);

        assertThat(response.documents()).isEqualTo(2);
        assertThat(response.chunks()).isGreaterThan(0);
        assertThat(Path.of(response.indexFile())).exists();
        assertThat(Path.of(properties.storeDir(), "index-fixed.json")).exists();
    }

    @Test
    void bothStrategiesProduceDifferentChunking() {
        Day21IngestResponse fixed = facade.ingest(Day21Properties.STRATEGY_FIXED);
        Day21IngestResponse structural = facade.ingest(Day21Properties.STRATEGY_STRUCTURAL);

        assertThat(fixed.chunks()).isGreaterThan(0);
        assertThat(structural.chunks()).isGreaterThan(0);
        assertThat(facade.chunks(Day21Properties.STRATEGY_FIXED))
                .extracting(Day21Chunk::strategy)
                .containsOnly(Day21Properties.STRATEGY_FIXED);
        assertThat(facade.chunks(Day21Properties.STRATEGY_STRUCTURAL))
                .extracting(Day21Chunk::strategy)
                .containsOnly(Day21Properties.STRATEGY_STRUCTURAL);
    }

    @Test
    void searchReturnsTopHitWithMetadataFromCorpus() {
        facade.ingest(Day21Properties.STRATEGY_FIXED);

        Day21SearchResponse response = facade.search(Day21Properties.STRATEGY_FIXED,
                "что такое эмбеддинги документов", 3);

        assertThat(response.hits()).isNotEmpty();
        Day21SearchHit top = response.hits().get(0);
        assertThat(top.chunk().fileName()).isEqualTo("custom.md");
        assertThat(top.chunk().title()).isEqualTo("Пользовательский корпус тестов");
        assertThat(top.chunk().section()).isEqualTo("Пользовательский корпус тестов");
        assertThat(top.score()).isGreaterThan(0.0);
        assertThat(top.snippet()).isNotBlank();
    }

    @Test
    void indexIsReloadedFromDiskWithoutReindexing() {
        facade.ingest(Day21Properties.STRATEGY_FIXED);

        Day21IndexFacade reloaded = buildFacade();
        Day21SearchResponse response = reloaded.search(Day21Properties.STRATEGY_FIXED,
                "долговременная память агента", 3);

        assertThat(response.hits()).isNotEmpty();
        assertThat(response.hits().get(0).chunk().fileName()).isEqualTo("custom.md");
    }

    @Test
    void compareReportsMetricsForBothStrategiesAndAVerdict() {
        facade.ingest(Day21Properties.STRATEGY_FIXED);
        facade.ingest(Day21Properties.STRATEGY_STRUCTURAL);

        Day21ComparisonResponse response = facade.compare();

        assertThat(response.strategies()).hasSize(2);
        for (Day21StrategyMetric metric : response.strategies()) {
            assertThat(metric.chunks()).isGreaterThan(0);
            assertThat(metric.probeTotal()).isEqualTo(6);
        }
        assertThat(response.probes()).hasSize(6);
        assertThat(response.verdict()).isNotBlank();
    }

    @Test
    void blankQueryIsRejected() {
        assertThatThrownBy(() -> facade.search(Day21Properties.STRATEGY_FIXED, "   ", 3))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Day21IndexFacade buildFacade() {
        Day21CorpusLoader loader = new Day21CorpusLoader(properties);
        Day21EmbeddingService embedding = new Day21EmbeddingService(properties);
        Day21IndexStore store = new Day21IndexStore(properties);
        return new Day21IndexFacade(loader, embedding, store, properties, List.of(
                new Day21FixedChunker(properties),
                new Day21StructuralChunker(properties)));
    }
}