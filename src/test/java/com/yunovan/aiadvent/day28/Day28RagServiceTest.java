package com.yunovan.aiadvent.day28;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21HealthResponse;
import com.yunovan.aiadvent.day21.Day21IndexFacade;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day21.Day21SearchResponse;
import com.yunovan.aiadvent.day21.Day21StrategyInfo;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26MockOllama;
import com.yunovan.aiadvent.day26.Day26Properties;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.RestClient;

class Day28RagServiceTest {

    private Day26MockOllama ollama;
    private Day21IndexFacade facade;
    private LlmClient cloud;
    private Day26Properties localProperties;
    private LlmProperties cloudProperties;

    @BeforeEach
    void setUp() {
        ollama = Day26MockOllama.start();
        facade = mock(Day21IndexFacade.class);
        cloud = mock(LlmClient.class);
        localProperties = new Day26Properties(ollama.endpoint(), null, null, null, null, null);
        cloudProperties = new LlmProperties("test-key", null, "openai/gpt-4o-mini");
        stubTwoChunks();
    }

    @AfterEach
    void tearDown() {
        ollama.close();
    }

    private Day28RagService service() {
        return service(new Day28Properties(null, null, null, null, null, null, null),
                cloudProperties);
    }

    private Day28RagService service(Day28Properties properties, LlmProperties llmProperties) {
        return new Day28RagService(facade, properties, localProperties,
                new Day26LocalLlmClient(RestClient.builder(), localProperties),
                cloud, llmProperties);
    }

    private static Day21Chunk embeddingsChunk() {
        return new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                "Эмбеддинги", "Признаковые алгоритмы", "articles/embeddings.md#fixed#0001",
                0, 100, "Эмбеддинги — это векторы, а n-граммы становятся признаками.");
    }

    private static Day21Chunk borschChunk() {
        return new Day21Chunk("fixed", "articles/random.md", "random.md",
                "Прочее", "Случайное", "articles/random.md#fixed#0002",
                0, 60, "Рецепт борща из свёклы режет морковь кубиками.");
    }

    private void stubTwoChunks() {
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse(
                "fixed", "эмбеддинги", 10,
                List.of(new Day21SearchHit(embeddingsChunk(), 0.85, "эмбеддинги и векторы"))));
        when(facade.chunks(any())).thenReturn(List.of(embeddingsChunk(), borschChunk()));
    }

    private static LlmReply cloudReply() {
        return new LlmReply("Ответ с эмбеддингами и векторами", "stop",
                45, 25, 70, null, 1_000L);
    }

    @Test
    void questionsReturnsTenControlQuestionsWithExpectations() {
        List<com.yunovan.aiadvent.day22.Day22ControlQuestion> questions = service().questions();

        assertThat(questions).hasSize(10);
        for (com.yunovan.aiadvent.day22.Day22ControlQuestion question : questions) {
            assertThat(question.id()).isNotBlank();
            assertThat(question.question()).isNotBlank();
            assertThat(question.expectedKeywords()).isNotEmpty();
            assertThat(question.expectedSources()).isNotEmpty();
        }
    }

    @Test
    void healthExposesIndexSearchLocalServerAndCloudKey() {
        when(facade.health()).thenReturn(new Day21HealthResponse(
                "index", "0.1.0", 9, 131000, 44,
                List.of(new Day21StrategyInfo("fixed", "описание", true, 100))));

        Day28HealthResponse health = service().health();

        assertThat(health.documents()).isEqualTo(9);
        assertThat(health.corpusChars()).isEqualTo(131000);
        assertThat(health.strategy()).isEqualTo("fixed");
        assertThat(health.topKBefore()).isEqualTo(10);
        assertThat(health.topKAfter()).isEqualTo(5);
        assertThat(health.threshold()).isEqualTo(0.5);
        assertThat(health.rewrite()).isTrue();
        assertThat(health.answerMaxTokens()).isEqualTo(400);
        assertThat(health.evaluateRuns()).isEqualTo(2);
        assertThat(health.retrieval()).contains("локальный").contains("без сетевых вызовов");
        assertThat(health.localEndpoint()).isEqualTo(ollama.endpoint());
        assertThat(health.localModel()).isEqualTo(Day26Properties.DEFAULT_MODEL);
        assertThat(health.localAvailable()).isTrue();
        assertThat(health.localVersion()).isEqualTo(Day26MockOllama.VERSION);
        assertThat(health.modelInstalled()).isTrue();
        assertThat(health.cloudConfigured()).isTrue();
        assertThat(health.cloudModel()).isEqualTo("openai/gpt-4o-mini");
        assertThat(health.usesCloud()).isFalse();
        assertThat(health.localError()).isEmpty();
    }

    @Test
    void healthReportsStoppedLocalServerInsteadOfThrowing() {
        when(facade.health()).thenReturn(new Day21HealthResponse(
                "index", "0.1.0", 9, 131000, 44, List.of()));
        ollama.close();

        Day28HealthResponse health = service().health();

        assertThat(health.localAvailable()).isFalse();
        assertThat(health.localVersion()).isEmpty();
        assertThat(health.modelInstalled()).isFalse();
        assertThat(health.localError()).contains("недоступен");
        assertThat(health.usesCloud()).isFalse();
    }

    @Test
    void healthWithoutCloudKeyReportsCloudAsNotConfigured() {
        when(facade.health()).thenReturn(new Day21HealthResponse(
                "index", "0.1.0", 9, 131000, 44, List.of()));

        Day28HealthResponse health = service(properties(),
                new LlmProperties(null, null, null)).health();

        assertThat(health.cloudConfigured()).isFalse();
        assertThat(health.cloudModel()).isEmpty();
    }

    @Test
    void askSendsSharedContextToLocalModelOnly() throws Exception {
        Day28AnswerResponse answer = service().ask("Что такое эмбеддинги?");

        String body = ollama.lastChatBody();
        assertThat(body).contains("Что такое эмбеддинги?")
                .contains("Контекст документов")
                .contains("articles/embeddings.md")
                .contains("Эмбеддинги — это векторы");
        assertThat(answer.engine()).isEqualTo(Day28RagService.ENGINE_LOCAL);
        assertThat(answer.model()).isEqualTo(Day26Properties.DEFAULT_MODEL);
        assertThat(answer.answer()).isEqualTo(Day26MockOllama.CYRILLIC_REPLY);
        assertThat(answer.matchedQuery()).contains("эмбеддинги").contains("n-грамм");
        assertThat(answer.rewritten()).isTrue();
        assertThat(answer.candidatesBefore()).isEqualTo(2);
        assertThat(answer.filteredOut()).isEqualTo(1);
        assertThat(answer.retrievedHits()).hasSize(1);
        assertThat(answer.sources()).containsExactly("embeddings.md");
        assertThat(answer.fallback()).isFalse();
        assertThat(answer.unavailableReason()).isEmpty();
        assertThat(answer.groundingPercent()).isNotNull();
        assertThat(answer.latencyMs()).isNotNegative();
        assertThat(answer.promptTokens()).isEqualTo(45);
        assertThat(answer.outputTokens()).isEqualTo(25);
        assertThat(answer.tokensPerSecond()).isPositive();
        verify(cloud, never()).complete(any(CompletionCommand.class));
    }

    @Test
    void askFallsBackToIndexWhenLocalModelIsDown() {
        ollama.close();

        Day28AnswerResponse answer = service().ask("Что такое эмбеддинги?");

        assertThat(answer.fallback()).isTrue();
        assertThat(answer.answer()).contains("Запрос:")
                .contains("контекста локального индекса")
                .contains("Эмбеддинги — это векторы");
        assertThat(answer.unavailableReason()).contains("недоступен");
        assertThat(answer.sources()).containsExactly("embeddings.md");
        assertThat(answer.groundingPercent()).isNotNull();
        assertThat(answer.latencyMs()).isNotNegative();
        verify(cloud, never()).complete(any(CompletionCommand.class));
    }

    @Test
    void blankQuestionsAreRejectedBeforeAnyCall() {
        Day28RagService service = service();

        assertThatThrownBy(() -> service.ask("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Вопрос не может быть пустым");
        assertThatThrownBy(() -> service.ask(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Вопрос не может быть пустым");
        assertThatThrownBy(() -> service.compare("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Вопрос не может быть пустым");
    }

    @Test
    void compareSendsSameLocalContextToBothEngines() {
        when(cloud.complete(any(CompletionCommand.class))).thenReturn(cloudReply());

        Day28CompareResponse compare = service().compare("Что такое эмбеддинги?");

        assertThat(compare.cloudConfigured()).isTrue();
        assertThat(compare.sources()).containsExactly("embeddings.md");
        assertThat(compare.local().engine()).isEqualTo(Day28RagService.ENGINE_LOCAL);
        assertThat(compare.local().fallback()).isFalse();
        assertThat(compare.cloud().engine()).isEqualTo(Day28RagService.ENGINE_CLOUD);
        assertThat(compare.cloud().answer()).isEqualTo("Ответ с эмбеддингами и векторами");
        assertThat(compare.cloud().model()).isEqualTo("openai/gpt-4o-mini");
        assertThat(compare.cloud().latencyMs()).isEqualTo(1_000L);
        assertThat(compare.cloud().promptTokens()).isEqualTo(45);
        assertThat(compare.cloud().outputTokens()).isEqualTo(25);
        assertThat(compare.cloud().tokensPerSecond()).isEqualTo(25.0);
        assertThat(compare.cloud().unavailableReason()).isEmpty();
        assertThat(compare.verdict())
                .contains("Один и тот же локальный контекст")
                .contains("embeddings.md")
                .contains("Опора на контекст")
                .contains("Быстрее");

        ArgumentCaptor<CompletionCommand> captor = ArgumentCaptor.forClass(CompletionCommand.class);
        verify(cloud).complete(captor.capture());
        assertThat(captor.getValue().prompt())
                .contains("Что такое эмбеддинги?")
                .contains("Контекст документов")
                .contains("articles/embeddings.md");
        assertThat(captor.getValue().systemPrompt()).contains("по-русски");
        assertThat(captor.getValue().maxTokens()).isEqualTo(400);
        assertThat(ollama.lastChatBody()).contains("Контекст документов");
    }

    @Test
    void compareWithoutCloudKeyExplainsWhyCloudSideIsMissing() {
        Day28CompareResponse compare = service(properties(),
                new LlmProperties(null, null, null)).compare("Что такое эмбеддинги?");

        assertThat(compare.cloudConfigured()).isFalse();
        assertThat(compare.cloud().unavailableReason()).contains("ключ не задан");
        assertThat(compare.cloud().answer()).isEmpty();
        assertThat(compare.verdict()).contains("Сравнить не с чем");
        verify(cloud, never()).complete(any(CompletionCommand.class));
    }

    @Test
    void compareKeepsLocalAnswerWhenCloudCallFails() {
        when(cloud.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("API error 429"));

        Day28CompareResponse compare = service().compare("Что такое эмбеддинги?");

        assertThat(compare.local().answer()).isEqualTo(Day26MockOllama.CYRILLIC_REPLY);
        assertThat(compare.local().fallback()).isFalse();
        assertThat(compare.cloud().unavailableReason()).contains("429");
        assertThat(compare.verdict()).contains("облачная модель недоступна");
    }

    @Test
    void evaluateAggregatesBothEnginesAcrossTwoRuns() {
        when(cloud.complete(any(CompletionCommand.class))).thenReturn(cloudReply());

        Day28EvalResponse eval = service().evaluate();

        assertThat(eval.total()).isEqualTo(10);
        assertThat(eval.runs()).isEqualTo(2);
        assertThat(eval.items()).hasSize(10);
        assertThat(eval.retrievalHits()).isEqualTo(1);
        assertThat(eval.retrievalRecallPercent()).isEqualTo(10.0);

        for (Day28EvalItem item : eval.items()) {
            assertThat(item.expectedKeywords()).isNotEmpty();
            assertThat(item.localCoverages()).hasSize(2);
            assertThat(item.localLatencies()).hasSize(2);
            assertThat(item.cloudCoverages()).hasSize(2);
            assertThat(item.cloudLatencies()).hasSize(2);
        }
        Day28EvalItem first = eval.items().getFirst();
        assertThat(first.retrievalHit()).isTrue();
        assertThat(first.retrievedSources()).containsExactly("embeddings.md");

        assertThat(eval.local().available()).isTrue();
        assertThat(eval.local().answered()).isEqualTo(20);
        assertThat(eval.local().fallbacks()).isZero();
        assertThat(eval.local().avgCoveragePercent()).isNotNull();
        assertThat(eval.local().coverageStdDev()).isNotNull();
        assertThat(eval.local().avgLatencyMs()).isNotNull();

        assertThat(eval.cloud().available()).isTrue();
        assertThat(eval.cloud().answered()).isEqualTo(20);
        assertThat(eval.cloud().avgCoveragePercent()).isPositive();
        assertThat(eval.cloud().avgLatencyMs()).isEqualTo(1_000.0);

        assertThat(eval.qualityVerdict())
                .contains("покрытие ключевых слов")
                .contains("локальная модель")
                .contains("против облачной");
        assertThat(eval.speedVerdict()).contains("Средняя задержка").contains("Быстрее");
        assertThat(eval.stabilityVerdict()).contains("п.п.");
    }

    @Test
    void evaluateWithoutCloudKeySkipsCloudEngineEntirely() {
        Day28RagService service = service(properties(),
                new LlmProperties(null, null, null));

        Day28EvalResponse eval = service.evaluate();

        assertThat(eval.local().available()).isTrue();
        assertThat(eval.cloud().available()).isFalse();
        assertThat(eval.cloud().reason()).contains("LLM_API_KEY");
        assertThat(eval.items()).allSatisfy(item -> {
            assertThat(item.localCoverages()).hasSize(2);
            assertThat(item.cloudCoverages()).isEmpty();
        });
        assertThat(eval.qualityVerdict()).contains("облачная модель не участвовала");
        verify(cloud, never()).complete(any(CompletionCommand.class));
    }

    @Test
    void evaluateStopsCloudEngineAfterApiFailure() {
        when(cloud.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("API error 429"));

        Day28EvalResponse eval = service().evaluate();

        assertThat(eval.cloud().available()).isFalse();
        assertThat(eval.cloud().reason()).contains("429");
        assertThat(eval.cloud().answered()).isZero();
        assertThat(eval.local().available()).isTrue();
        assertThat(eval.qualityVerdict()).contains("облачная модель не участвовала");
    }

    @Test
    void evaluateReportsLocalEngineUnavailableWithoutFakeMetrics() {
        ollama.close();
        when(cloud.complete(any(CompletionCommand.class))).thenReturn(cloudReply());

        Day28EvalResponse eval = service().evaluate();

        assertThat(eval.local().available()).isFalse();
        assertThat(eval.local().reason()).contains("недоступен");
        assertThat(eval.local().answered()).isZero();
        assertThat(eval.local().avgCoveragePercent()).isNull();
        assertThat(eval.items()).allSatisfy(item -> {
            assertThat(item.localCoverages()).isEmpty();
            assertThat(item.cloudCoverages()).hasSize(2);
        });
        assertThat(eval.cloud().available()).isTrue();
        assertThat(eval.qualityVerdict()).contains("Локальная модель не участвовала");
    }

    @Test
    void evaluateWithSingleRunReportsStabilityAsNotMeasured() {
        Day28RagService service = service(
                new Day28Properties(null, null, null, null, null, null, 1), cloudProperties);

        Day28EvalResponse eval = service.evaluate();

        assertThat(eval.runs()).isEqualTo(1);
        assertThat(eval.local().answered()).isEqualTo(10);
        assertThat(eval.local().coverageStdDev()).isNull();
        assertThat(eval.stabilityVerdict()).contains("не измерялась");
    }

    @Test
    void keywordCoverageCountsExpectedTerms() {
        assertThat(Day28RagService.keywordCoverage(List.of("эмбеддинг", "вектор"),
                "Здесь говорится про эмбеддинги и векторы.")).isEqualTo(1.0);
        assertThat(Day28RagService.keywordCoverage(List.of("эмбеддинг", "вектор"),
                "Только про векторы.")).isEqualTo(0.5);
        assertThat(Day28RagService.keywordCoverage(List.of("эмбеддинг"), "  ")).isZero();
        assertThat(Day28RagService.keywordCoverage(List.of(), "любой ответ")).isZero();
    }

    private static Day28Properties properties() {
        return new Day28Properties(null, null, null, null, null, null, null);
    }
}
