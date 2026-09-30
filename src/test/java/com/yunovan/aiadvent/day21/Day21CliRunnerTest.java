package com.yunovan.aiadvent.day21;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day21CliRunnerTest {

    private final Day21IndexFacade facade = mock(Day21IndexFacade.class);
    private final Day21AgentService service = mock(Day21AgentService.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day21CliRunner runner = new Day21CliRunner(facade, service, props(), context);

    private static Day21Properties props() {
        return new Day21Properties(0, "/mcp", "ai-advent-index-mcp", "0.1.0",
                "build/day21-index", "build/day21-corpus", null, null, null);
    }

    private String run(String... args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.run(new DefaultApplicationArguments(args));
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }

    @Test
    void otherDayTriggersNoInteractions() {
        runner.run(new DefaultApplicationArguments("--day=20", "--check"));

        verifyNoInteractions(facade);
        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsCorpusSummary() {
        when(facade.health()).thenReturn(new Day21HealthResponse(
                "ai-advent-index-mcp", "0.1.0", 9, 131000, 44,
                List.of(new Day21StrategyInfo("fixed", "описание", true, 100),
                        new Day21StrategyInfo("structural", "описание", false, 0))));

        String output = run("--day=21", "--check");

        assertThat(output)
                .contains("ИНДЕКС ДОКУМЕНТОВ")
                .contains("документов в корпусе: 9")
                .contains("страниц")
                .contains("fixed: построен")
                .contains("structural: не построен");
    }

    @Test
    void ingestPrintsBuildSummary() {
        when(facade.ingest(any())).thenReturn(new Day21IngestResponse(
                Day21Properties.STRATEGY_FIXED, 9, 123, 131000,
                "data/day21-index/index-fixed.json"));

        String output = run("--day=21", "--ingest");

        assertThat(output)
                .contains("ПОСТРОЕНИЕ ИНДЕКСА")
                .contains("стратегия: fixed")
                .contains("чанков: 123")
                .contains("index-fixed.json");
    }

    @Test
    void searchPrintsTopHits() {
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse(
                "fixed", "память", 3,
                List.of(new Day21SearchHit(
                        new Day21Chunk("fixed", "articles/memory.md", "memory.md", "Память",
                                "Долговременная память", "articles/memory.md#fixed#0001", 0, 90, "текст"),
                        0.8, "про память агента"))));

        String output = run("--day=21", "--search=память");

        assertThat(output)
                .contains("ПОИСК ПО ИНДЕКСУ: память")
                .contains("articles/memory.md#fixed#0001")
                .contains("section: Долговременная память");
    }

    @Test
    void comparePrintsMetricsAndVerdict() {
        when(facade.compare()).thenReturn(new Day21ComparisonResponse(
                List.of(new Day21StrategyMetric(
                        "fixed", 9, 100, 130000, 500.0, 300.0, 900.0, 130.0, 0.26, 5, 6, 83.3),
                        new Day21StrategyMetric(
                                "structural", 9, 80, 130000, 600.0, 400.0, 850.0, 80.0, 0.13, 6, 6, 100.0)),
                List.of("n грамм"), "Лучше structural"));

        String output = run("--day=21", "--compare");

        assertThat(output)
                .contains("СРАВНЕНИЕ СТРАТЕГИЙ ЧАНКИНГА")
                .contains("CV: 0.26")
                .contains("83.3%")
                .contains("Вердикт: Лучше structural");
    }

    @Test
    void chunksPrintsMetadataRows() {
        when(facade.chunks(any())).thenReturn(List.of(
                new Day21Chunk("structural", "code/TokenizerSample.java", "TokenizerSample.java",
                        "TokenizerSample", "TokenizerSample", "code/TokenizerSample.java#structural#0001",
                        0, 200, "код")));

        String output = run("--day=21", "--chunks", "--limit=1");

        assertThat(output)
                .contains("ЧАНКИ ИНДЕКСА")
                .contains("code/TokenizerSample.java#structural#0001")
                .contains("title: TokenizerSample");
    }

    @Test
    void agentPrintsIntentToolAndAnswer() {
        when(service.submit("найди память")).thenReturn(new Day21AgentResponse(
                "найди память", "search", "index_search",
                Map.of("query", "память", "strategy", "fixed"),
                "Найдено по запросу", "Память описана в articles/memory.md"));

        String output = run("--day=21", "--prompt=найди память");

        assertThat(output)
                .contains("АГЕНТ / ИНДЕКС")
                .contains("intent: search")
                .contains("tool: index_search")
                .contains("Память описана в articles/memory.md");
    }

    @Test
    void withoutArgumentsPrintsUsage() {
        String output = run("--day=21");

        assertThat(output).contains("--check").contains("--search").contains("--compare");
    }

    @Test
    void indexFailurePrintsErrorMessage() {
        when(facade.health())
                .thenThrow(new Day21IndexException("Не удалось сохранить индекс"));

        String output = run("--day=21", "--check");

        assertThat(output).contains("ОШИБКА").contains("Не удалось сохранить индекс");
    }
}