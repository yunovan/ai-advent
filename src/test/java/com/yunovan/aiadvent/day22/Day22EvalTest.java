package com.yunovan.aiadvent.day22;

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

class Day22EvalTest {

    @TempDir
    Path tempDir;

    private Day22RagService service;
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
        service = new Day22RagService(facade, new Day22Properties(null, null, null), llm);
    }

    @Test
    void ragModeRetrievesExpectedDocumentForQuestion() {
        Day22AnswerResponse response = service.answer(
                "Что такое эмбеддинги документов и как они получаются?", "rag");

        assertThat(response.mode()).isEqualTo(Day22RagService.MODE_RAG);
        assertThat(response.retrievedHits()).isNotEmpty();
        assertThat(response.retrievedHits().stream()
                .map(hit -> hit.chunk().fileName()).toList())
                .contains("embeddings.md");
        assertThat(response.answer()).contains("embeddings.md");
        assertThat(response.answer()).contains("эмбеддинг");
    }

    @Test
    void ragModeRetrievesSourceCodeForCodeQuestion() {
        Day22AnswerResponse response = service.answer("Как устроено асинхронное логирование в QueuedLogger?", "rag");

        assertThat(response.retrievedHits()).isNotEmpty();
        assertThat(response.retrievedHits().stream()
                .map(hit -> hit.chunk().fileName()).toList())
                .contains("QueuedLogger.java");
    }

    @Test
    void plainModeComesWithoutSources() {
        Day22AnswerResponse response = service.answer("Что такое эмбеддинги документов?", "plain");

        assertThat(response.mode()).isEqualTo(Day22RagService.MODE_PLAIN);
        assertThat(response.retrievedHits()).isEmpty();
        assertThat(response.answer()).doesNotContain("embeddings.md");
    }

    @Test
    void evaluateRunsTenControlQuestionsAndScoresBothModes() {
        Day22EvalResponse evaluate = service.evaluate();

        assertThat(evaluate.total()).isEqualTo(10);
        assertThat(evaluate.items()).hasSize(10);
        for (Day22EvalItem item : evaluate.items()) {
            assertThat(item.expectedKeywords()).isNotEmpty();
            assertThat(item.expectedSources()).isNotEmpty();
        }
        assertThat(evaluate.retrievalHits()).isGreaterThan(0);
        assertThat(evaluate.retrievalRecallPercent()).isPositive();
        assertThat(evaluate.ragAvgCoveragePercent()).isGreaterThan(evaluate.plainAvgCoveragePercent());
        assertThat(evaluate.avgCoverageGapPercent()).isPositive();
        assertThat(evaluate.verdict()).contains("RAG");
    }

    @Test
    void embeddingsControlQuestionRetrievesItsSource() {
        Day22EvalResponse evaluate = service.evaluate();

        Day22EvalItem embeddings = evaluate.items().stream()
                .filter(item -> item.id().equals("q01"))
                .findFirst()
                .orElseThrow();
        assertThat(embeddings.retrievedSources()).contains("embeddings.md");
        assertThat(embeddings.retrievalHit()).isTrue();
        assertThat(embeddings.ragCoveragePercent()).isGreaterThan(0);
        assertThat(embeddings.ragCoveragePercent()).isGreaterThan(embeddings.plainCoveragePercent());
    }

    @Test
    void ragAnswersAreGroundedInSources() {
        Day22EvalResponse evaluate = service.evaluate();

        long grounded = evaluate.items().stream()
                .filter(item -> item.retrievalHit() && item.ragCoveragePercent() > 0)
                .count();
        assertThat(grounded).isGreaterThan(0);
    }
}