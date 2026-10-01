package com.yunovan.aiadvent.day23;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21CorpusLoader;
import com.yunovan.aiadvent.day21.Day21EmbeddingService;
import com.yunovan.aiadvent.day21.Day21FixedChunker;
import com.yunovan.aiadvent.day21.Day21IndexFacade;
import com.yunovan.aiadvent.day21.Day21IndexStore;
import com.yunovan.aiadvent.day21.Day21Properties;
import com.yunovan.aiadvent.day21.Day21StructuralChunker;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day23EvalTest {

    @TempDir
    Path tempDir;

    private Day23RagService service;
    private LlmClient llm;

    @BeforeEach
    void setUp() {
        Day21Properties properties = new Day21Properties(
                0, "/mcp", "test", "0.0.1",
                tempDir.resolve("store").toString(), tempDir.resolve("corpus").toString(),
                600, 80, 512);
        Day21IndexFacade facade = new Day21IndexFacade(
                new Day21CorpusLoader(properties),
                new Day21EmbeddingService(properties),
                new Day21IndexStore(properties), properties,
                List.of(new Day21FixedChunker(properties), new Day21StructuralChunker(properties)));
        llm = mock(LlmClient.class);
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));
        service = new Day23RagService(facade, new Day23Properties(null, null, null, null, null, null), llm);
    }

    @Test
    void baseAndFullModesRetrieveExpectedDocument() {
        Day23AnswerResponse base = service.answer(
                "Что такое эмбеддинги документов и как они получаются?", "base");
        Day23AnswerResponse full = service.answer(
                "Что такое эмбеддинги документов и как они получаются?", "full");

        assertThat(base.retrievedHits().stream().map(h -> h.chunk().fileName()))
                .contains("embeddings.md");
        assertThat(full.retrievedHits().stream().map(h -> h.chunk().fileName()))
                .contains("embeddings.md");
        assertThat(full.rewritten()).isTrue();
        assertThat(full.matchedQuery()).contains("вектор");
    }

    @Test
    void filterDoesNotBreakSourceCodeQuestion() {
        Day23AnswerResponse response = service.answer(
                "Как устроено асинхронное логирование в QueuedLogger?", "full");

        assertThat(response.retrievedHits().stream().map(h -> h.chunk().fileName()))
                .contains("QueuedLogger.java");
    }

    @Test
    void evaluateRunsTenQuestionsAndScoresBaseVsFull() {
        Day23EvalResponse evaluate = service.evaluate();

        assertThat(evaluate.total()).isEqualTo(10);
        assertThat(evaluate.items()).hasSize(10);
        assertThat(evaluate.baseRetrievalHits()).isGreaterThan(0);
        assertThat(evaluate.fullRetrievalHits()).isGreaterThan(0);
        assertThat(evaluate.baseRecallPercent()).isPositive();
        assertThat(evaluate.fullRecallPercent()).isPositive();
        assertThat(evaluate.fullRecallPercent())
                .isGreaterThanOrEqualTo(evaluate.baseRecallPercent());
        assertThat(evaluate.totalFilteredOut()).isGreaterThan(0);
        assertThat(evaluate.improved()).isGreaterThan(0);
        assertThat(evaluate.verdict()).contains("фильтр");
    }

    @Test
    void fullCoverageIsAboveBaseCoverage() {
        Day23EvalResponse evaluate = service.evaluate();

        assertThat(evaluate.avgCoverageFullPercent())
                .isGreaterThan(evaluate.avgCoverageBasePercent());
    }

    @Test
    void fullModeLeavesOnlyRelevantSourcesForEmbeddingsQuestion() {
        Day23EvalResponse evaluate = service.evaluate();

        Day23EvalItem embeddings = evaluate.items().stream()
                .filter(item -> item.id().equals("q01"))
                .findFirst()
                .orElseThrow();
        assertThat(embeddings.fullSources()).contains("embeddings.md");
        assertThat(embeddings.fullSources()).doesNotContain("README.md");
    }

    @Test
    void embeddingsControlQuestionHitsWithFilter() {
        Day23EvalResponse evaluate = service.evaluate();

        Day23EvalItem embeddings = evaluate.items().stream()
                .filter(item -> item.id().equals("q01"))
                .findFirst()
                .orElseThrow();
        assertThat(embeddings.baseHit()).isTrue();
        assertThat(embeddings.fullHit()).isTrue();
        assertThat(embeddings.fullSources()).contains("embeddings.md");
    }
}