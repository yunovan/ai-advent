package com.yunovan.aiadvent.day24;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

class Day24ServiceTest {

    private final Day21IndexFacade facade = mock(Day21IndexFacade.class);
    private final LlmClient llm = mock(LlmClient.class);

    private Day24Service service() {
        return new Day24Service(facade, new Day24Properties(null, null, null, null, null,
                null, null, null, null, null), llm);
    }

    private Day21Chunk embeddingsChunk() {
        return new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                "Эмбеддинги", "Признаковые алгоритмы", "articles/embeddings.md#fixed#0001",
                0, 100, "Эмбеддинги — это векторы, а n-граммы становятся признаками. "
                + "Чем больше признаков в основе, тем точнее вектор.");
    }

    private Day21Chunk borschChunk() {
        return new Day21Chunk("fixed", "articles/random.md", "random.md",
                "Прочее", "Случайное", "articles/random.md#fixed#0002",
                0, 60, "Рецепт борща из свёклы режет морковь кубиками.");
    }

    private Day21SearchHit hit() {
        return new Day21SearchHit(embeddingsChunk(), 0.85, "эмбеддинги и векторы");
    }

    private Day21SearchHit noOverlapHit() {
        return new Day21SearchHit(borschChunk(), 0.12, "борщ");
    }

    private void stubKnown() {
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse(
                "fixed", "эмбеддинги", 10, List.of(hit())));
        when(facade.chunks(any())).thenReturn(List.of(embeddingsChunk(), borschChunk()));
    }

    private void stubWeak() {
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse(
                "fixed", "погода", 10, List.of(noOverlapHit())));
        when(facade.chunks(any())).thenReturn(List.of(borschChunk()));
    }

    @Test
    void questionsReturnsTenControlQuestionsWithExpectations() {
        List<Day24ControlQuestion> questions = service().questions();

        assertThat(questions).hasSize(10);
        for (Day24ControlQuestion question : questions) {
            assertThat(question.id()).isNotBlank();
            assertThat(question.question()).isNotBlank();
            assertThat(question.expectedSources()).isNotEmpty();
        }
    }

    @Test
    void knownQuestionReturnsAnswerWithSourcesAndQuotes() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day24GroundedResponse response = service().answer("Что такое эмбеддинги документов?");

        assertThat(response.unknown()).isFalse();
        assertThat(response.sources()).isNotEmpty();
        assertThat(response.quotes()).isNotEmpty();
        assertThat(response.answer()).isNotBlank();
        assertThat(response.fallback()).isTrue();
    }

    @Test
    void sourcesCarrySourceSectionAndChunkId() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day24GroundedResponse response = service().answer("Что такое эмбеддинги документов?");

        Day24Source source = response.sources().get(0);
        assertThat(source.source()).isEqualTo("articles/embeddings.md");
        assertThat(source.section()).isEqualTo("Признаковые алгоритмы");
        assertThat(source.chunkId()).isEqualTo("articles/embeddings.md#fixed#0001");
        assertThat(source.score()).isPositive();
    }

    @Test
    void quotesAreVerbatimFragmentsOfChunksWithOrigin() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day24GroundedResponse response = service().answer("Что такое эмбеддинги документов?");

        assertThat(response.quotes()).isNotEmpty();
        for (Day24Quote quote : response.quotes()) {
            assertThat(embeddingsChunk().text()).contains(quote.text().substring(0, 20));
            assertThat(quote.source()).isEqualTo("articles/embeddings.md");
            assertThat(quote.section()).isNotBlank();
            assertThat(quote.chunkId()).isNotBlank();
            assertThat(quote.matchedKeywords()).isGreaterThan(0);
        }
        assertThat(response.answer()).contains(response.quotes().get(0).text());
    }

    @Test
    void weakQuestionTriggersUnknownAndAsksClarification() {
        stubWeak();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day24GroundedResponse response = service().answer("Какая сегодня погода в Москве?");

        assertThat(response.unknown()).isTrue();
        assertThat(response.answer()).containsIgnoringCase("не знаю");
        assertThat(response.answer()).containsIgnoringCase("уточните");
        assertThat(response.supported()).isFalse();
        assertThat(response.supportCoveragePercent()).isZero();
        assertThat(response.sources()).isEmpty();
        assertThat(response.quotes()).isEmpty();
    }

    @Test
    void denseOnlyMatchWithoutLexicalEvidenceTriggersUnknown() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day24GroundedResponse response = service().answer("Придумай название для космической ракеты");

        assertThat(response.unknown()).isTrue();
        assertThat(response.answer()).containsIgnoringCase("не знаю");
        assertThat(response.quotes()).isEmpty();
    }

    @Test
    void answerBelowUnknownThresholdSaysDontKnowEvenWithRelevantChunk() {
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse(
                "fixed", "эмбеддинги", 10, List.of()));
        when(facade.chunks(any())).thenReturn(List.of(embeddingsChunk()));
        Day24Service strict = new Day24Service(facade,
                new Day24Properties(null, null, null, 0.4, 1.0, null, null, null, null, null),
                llm);
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day24GroundedResponse response = strict.answer("Что такое эмбеддинги документов?");

        assertThat(response.unknown()).isTrue();
        assertThat(response.answer()).containsIgnoringCase("не знаю");
        assertThat(response.quotes()).isEmpty();
    }

    @Test
    void llmAnswerSupportedByQuotesWhenMeaningMatches() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Эмбеддинги — это векторы, а n-граммы становятся "
                        + "признаками.", "stop"));

        Day24GroundedResponse response = service().answer("Что такое эмбеддинги документов?");

        assertThat(response.fallback()).isFalse();
        assertThat(response.supported()).isTrue();
        assertThat(response.supportCoveragePercent()).isGreaterThan(50.0);
    }

    @Test
    void weakQuestionNotAnswerableWithLlmOnline() {
        stubWeak();
        when(llm.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Какая-то выдумка про Москву", "stop"));

        Day24GroundedResponse response = service().answer("Какая сегодня погода в Москве?");

        assertThat(response.unknown()).isTrue();
        assertThat(response.answer()).containsIgnoringCase("не знаю");
    }

    @Test
    void quoteSupportCoverageMeasuresAnswerGroundedness() {
        Day24Quote quote = new Day24Quote("articles/embeddings.md", "Признаковые алгоритмы",
                "chunk1", "Эмбеддинги — это векторы, а n-граммы становятся признаками.",
                0.9, 2);

        assertThat(Day24QuoteEngine.supportCoverage(
                "Эмбеддинги это векторы и n-граммы признаки", List.of(quote))).isGreaterThan(0.7);
        assertThat(Day24QuoteEngine.supportCoverage(
                "Про погоду и погодные явления в Москве", List.of(quote))).isLessThan(0.5);
        assertThat(Day24QuoteEngine.supportCoverage("  ", List.of(quote))).isZero();
    }

    @Test
    void evaluateReportsKnownComplianceAndWeakUnknownMode() {
        stubKnown();
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("offline"));

        Day24EvalResponse evaluate = service().evaluate();

        assertThat(evaluate.knownCount()).isEqualTo(10);
        assertThat(evaluate.sourcesPresent()).isGreaterThanOrEqualTo(1);
        assertThat(evaluate.quotesPresent()).isGreaterThanOrEqualTo(1);
        assertThat(evaluate.supportedCount()).isGreaterThanOrEqualTo(1);
        assertThat(evaluate.avgSupportPercent()).isGreaterThan(0);
        assertThat(evaluate.weakCount()).isEqualTo(2);
        assertThat(evaluate.unknownTriggered()).isEqualTo(2);
        assertThat(evaluate.verdict()).contains("Анти-галлюцинация");
        Day24EvalItem embeddings = evaluate.questions().stream()
                .filter(item -> item.id().equals("q01"))
                .findFirst()
                .orElseThrow();
        assertThat(embeddings.hasSources()).isTrue();
        assertThat(embeddings.hasQuotes()).isTrue();
        assertThat(embeddings.supported()).isTrue();
        assertThat(embeddings.sources()).contains("articles/embeddings.md");
    }

    @Test
    void blankQuestionIsRejected() {
        assertThatThrownBy(() -> service().answer("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустым");
    }

    @Test
    void healthExposesConfigurationAndWeakQuestions() {
        when(facade.health()).thenReturn(new Day21HealthResponse(
                "index", "0.1.0", 9, 131000, 44,
                List.of(new Day21StrategyInfo("fixed", "описание", true, 100))));
        Day24Service custom = new Day24Service(facade,
                new Day24Properties(null, 12, 4, 0.6, 0.4, true, 2, 30, 0.5, 500), llm);

        Day24HealthResponse health = custom.health();

        assertThat(health.documents()).isEqualTo(9);
        assertThat(health.strategy()).isEqualTo("fixed");
        assertThat(health.topKBefore()).isEqualTo(12);
        assertThat(health.topKAfter()).isEqualTo(4);
        assertThat(health.threshold()).isEqualTo(0.6);
        assertThat(health.unknownThreshold()).isEqualTo(0.4);
        assertThat(health.rewrite()).isTrue();
        assertThat(health.quotesPerSource()).isEqualTo(2);
        assertThat(health.quoteMinChars()).isEqualTo(30);
        assertThat(health.supportThreshold()).isEqualTo(0.5);
        assertThat(health.answerMaxTokens()).isEqualTo(500);
        assertThat(health.weakQuestions()).hasSize(2);
        assertThat(health.strategies()).containsExactly("fixed");
    }
}