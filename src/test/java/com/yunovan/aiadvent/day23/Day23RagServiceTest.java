package com.yunovan.aiadvent.day23;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21HealthResponse;
import com.yunovan.aiadvent.day21.Day21IndexFacade;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day21.Day21SearchResponse;
import com.yunovan.aiadvent.day21.Day21StrategyInfo;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmReply;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class Day23RagServiceTest {

    private final Day21IndexFacade facade = mock(Day21IndexFacade.class);
    private final LlmClient llm = mock(LlmClient.class);

    private Day23RagService service() {
        return new Day23RagService(facade, new Day23Properties(null, null, null, null, null, null), llm);
    }

    private Day21Chunk embeddingsChunk() {
        return new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                "Эмбеддинги", "Признаковые алгоритмы", "articles/embeddings.md#fixed#0001",
                0, 100, "Эмбеддинги — это векторы, а n-граммы становятся признаками.");
    }

    private Day21Chunk borschChunk() {
        return new Day21Chunk("fixed", "articles/random.md", "random.md",
                "Прочее", "Случайное", "articles/random.md#fixed#0002",
                0, 60, "Рецепт борща из свёклы режет морковь кубиками.");
    }

    private Day21SearchHit hit() {
        return new Day21SearchHit(embeddingsChunk(), 0.85, "эмбеддинги и векторы");
    }

    private void stubTwoChunks() {
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse(
                "fixed", "эмбеддинги", 10, List.of(hit())));
        when(facade.chunks(any())).thenReturn(List.of(embeddingsChunk(), borschChunk()));
    }

    @Test
    void questionsReturnsTenControlQuestionsWithExpectations() {
        List<Day23ControlQuestion> questions = service().questions();

        assertThat(questions).hasSize(10);
        for (Day23ControlQuestion question : questions) {
            assertThat(question.id()).isNotBlank();
            assertThat(question.question()).isNotBlank();
            assertThat(question.expectedKeywords()).isNotEmpty();
            assertThat(question.expectedSources()).isNotEmpty();
        }
    }

    @Test
    void baseModeDoesNotRewriteAndDoesNotFilter() {
        stubTwoChunks();
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day23AnswerResponse response = service().answer("Что такое эмбеддинги?", "base");

        assertThat(response.mode()).isEqualTo(Day23RagService.MODE_BASE);
        assertThat(response.rewritten()).isFalse();
        assertThat(response.matchedQuery()).isEqualTo("Что такое эмбеддинги?");
        assertThat(response.filteredOut()).isZero();
        assertThat(response.candidatesBefore()).isEqualTo(2);
        assertThat(response.retrievedHits()).hasSize(2);
    }

    @Test
    void rewriteModeExpandsQueryButKeepsAllCandidates() {
        stubTwoChunks();
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day23AnswerResponse response = service().answer("Что такое эмбеддинги?", "rewrite");

        assertThat(response.mode()).isEqualTo(Day23RagService.MODE_REWRITE);
        assertThat(response.rewritten()).isTrue();
        assertThat(response.matchedQuery()).contains("вектор");
        assertThat(response.matchedQuery()).contains("n-грамм");
        assertThat(response.filteredOut()).isZero();
        assertThat(response.candidatesBefore()).isEqualTo(2);
    }

    @Test
    void filterModeCutsIrrelevantChunksByThresholdWithoutRewrite() {
        stubTwoChunks();
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day23AnswerResponse response = service().answer("Что такое эмбеддинги?", "filter");

        assertThat(response.mode()).isEqualTo(Day23RagService.MODE_FILTER);
        assertThat(response.rewritten()).isFalse();
        assertThat(response.filteredOut()).isEqualTo(1);
        assertThat(response.candidatesBefore()).isEqualTo(2);
        assertThat(response.retrievedHits()).hasSize(1);
        assertThat(response.retrievedHits().get(0).chunk().fileName()).isEqualTo("embeddings.md");
    }

    @Test
    void fullModeCombinesRewriteAndFilter() {
        stubTwoChunks();
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day23AnswerResponse response = service().answer("Что такое эмбеддинги?", "full");

        assertThat(response.mode()).isEqualTo(Day23RagService.MODE_FULL);
        assertThat(response.rewritten()).isTrue();
        assertThat(response.matchedQuery()).contains("вектор");
        assertThat(response.filteredOut()).isEqualTo(1);
        assertThat(response.retrievedHits()).hasSize(1);
    }

    @Test
    void ragModePassesContextAndFilteringHintToLlm() {
        stubTwoChunks();
        when(llm.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Ответ с фильтром по эмбеддингам", "stop"));

        Day23AnswerResponse response = service().answer("Что такое эмбеддинги?", "full");

        ArgumentCaptor<CompletionCommand> captor = ArgumentCaptor.forClass(CompletionCommand.class);
        verify(llm).complete(captor.capture());
        CompletionCommand command = captor.getValue();
        assertThat(command.prompt()).contains("Что такое эмбеддинги?");
        assertThat(command.prompt()).contains("отсеяны как нерелевантные");
        assertThat(command.prompt()).contains("articles/embeddings.md");
        assertThat(command.prompt()).doesNotContain("articles/random.md");
        assertThat(command.systemPrompt()).contains("ТОЛЬКО");
        assertThat(response.fallback()).isFalse();
        assertThat(response.answer()).isEqualTo("Ответ с фильтром по эмбеддингам");
    }

    @Test
    void emptyResultsStillAnswerFromIndex() {
        when(facade.search(any(), any(), any())).thenReturn(
                new Day21SearchResponse("fixed", "свёкла", 10, List.of()));
        when(facade.chunks(any())).thenReturn(List.of(embeddingsChunk()));
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day23AnswerResponse response = service().answer("Рецепт борща со свёклой", "full");

        assertThat(response.fallback()).isTrue();
        assertThat(response.answer()).contains("ничего не найдено");
        assertThat(response.retrievedHits()).isEmpty();
    }

    @Test
    void rewriteEndpointShowsAppliedRules() {
        Day23RewriteResponse response = service().rewrite("Что такое эмбеддинги и MCP?");

        assertThat(response.applied()).isTrue();
        assertThat(response.original()).isEqualTo("Что такое эмбеддинги и MCP?");
        assertThat(response.rewritten()).contains("эмбеддинги");
        assertThat(response.rewritten()).contains("вектор");
        assertThat(response.rewritten()).contains("json-rpc");
        assertThat(response.expansions()).isNotEmpty();
    }

    @Test
    void normalizeModeMapsAllVariants() {
        assertThat(Day23RagService.normalizeMode(null)).isEqualTo(Day23RagService.MODE_FULL);
        assertThat(Day23RagService.normalizeMode("")).isEqualTo(Day23RagService.MODE_FULL);
        assertThat(Day23RagService.normalizeMode("base")).isEqualTo(Day23RagService.MODE_BASE);
        assertThat(Day23RagService.normalizeMode("без фильтра")).isEqualTo(Day23RagService.MODE_BASE);
        assertThat(Day23RagService.normalizeMode("filter")).isEqualTo(Day23RagService.MODE_FILTER);
        assertThat(Day23RagService.normalizeMode("с фильтром")).isEqualTo(Day23RagService.MODE_FILTER);
        assertThat(Day23RagService.normalizeMode("rewrite")).isEqualTo(Day23RagService.MODE_REWRITE);
        assertThat(Day23RagService.normalizeMode("переписать")).isEqualTo(Day23RagService.MODE_REWRITE);
        assertThat(Day23RagService.normalizeMode("full")).isEqualTo(Day23RagService.MODE_FULL);
        assertThat(Day23RagService.normalizeMode("всё вместе")).isEqualTo(Day23RagService.MODE_FULL);
    }

    @Test
    void askAutoDetectsRequestedPipeline() {
        stubTwoChunks();
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day23AnswerResponse full = service().ask("Что такое эмбеддинги?");
        Day23AnswerResponse base = service().ask("Ответь как есть, без фильтра: что такое эмбеддинги?");

        assertThat(full.mode()).isEqualTo(Day23RagService.MODE_FULL);
        assertThat(base.mode()).isEqualTo(Day23RagService.MODE_BASE);
    }

    @Test
    void blankQuestionIsRejected() {
        Day23RagService service = service();
        assertThatThrownBy(() -> service.answer("   ", "full"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустым");
        assertThatThrownBy(() -> service.rewrite("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустым");
        assertThatThrownBy(() -> service.compare("   ", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустым");
    }

    @Test
    void keywordCoverageCountsExpectedTerms() {
        assertThat(Day23RagService.keywordCoverage(List.of("эмбеддинг", "вектор"),
                "Здесь говорится про эмбеддинги и векторы.")).isEqualTo(1.0);
        assertThat(Day23RagService.keywordCoverage(List.of("эмбеддинг", "вектор"),
                "Только про векторы.")).isEqualTo(0.5);
        assertThat(Day23RagService.keywordCoverage(List.of("эмбеддинг"), "  ")).isZero();
    }

    @Test
    void compareDefaultsToBaseVsFullAndReportsFiltering() {
        stubTwoChunks();
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day23CompareResponse response = service().compare("Что такое эмбеддинги?", null, null);

        assertThat(response.first().mode()).isEqualTo(Day23RagService.MODE_BASE);
        assertThat(response.second().mode()).isEqualTo(Day23RagService.MODE_FULL);
        assertThat(response.sourcesAfter()).contains("embeddings.md");
        assertThat(response.sourcesAfter()).doesNotContain("random.md");
        assertThat(response.verdict()).contains("отсеяно по порогу");
        assertThat(response.verdict()).contains("embeddings.md");
    }

    @Test
    void healthExposesConfiguration() {
        when(facade.health()).thenReturn(new Day21HealthResponse(
                "index", "0.1.0", 9, 131000, 44,
                List.of(new Day21StrategyInfo("fixed", "описание", true, 100))));
        Day23RagService service = new Day23RagService(facade,
                new Day23Properties(null, 12, 4, 0.6, true, 500), llm);

        Day23HealthResponse health = service.health();

        assertThat(health.documents()).isEqualTo(9);
        assertThat(health.strategy()).isEqualTo("fixed");
        assertThat(health.topKBefore()).isEqualTo(12);
        assertThat(health.topKAfter()).isEqualTo(4);
        assertThat(health.threshold()).isEqualTo(0.6);
        assertThat(health.rewrite()).isTrue();
        assertThat(health.answerMaxTokens()).isEqualTo(500);
    }
}