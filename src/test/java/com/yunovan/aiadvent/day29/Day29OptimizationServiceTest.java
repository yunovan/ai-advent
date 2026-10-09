package com.yunovan.aiadvent.day29;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21IndexFacade;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day21.Day21SearchResponse;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26MockOllama;
import com.yunovan.aiadvent.day26.Day26Properties;
import com.yunovan.aiadvent.day28.Day28Properties;
import com.yunovan.aiadvent.day28.Day28RagService;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class Day29OptimizationServiceTest {

    private Day26MockOllama ollama;
    private Day21IndexFacade facade;
    private Day26Properties localProperties;
    private Day26LocalLlmClient localLlm;

    @BeforeEach
    void setUp() {
        ollama = Day26MockOllama.start();
        facade = mock(Day21IndexFacade.class);
        localProperties = new Day26Properties(ollama.endpoint(), null, null, null, null, null);
        localLlm = new Day26LocalLlmClient(RestClient.builder(), localProperties);
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse(
                "fixed", "эмбеддинги", 10,
                List.of(new Day21SearchHit(embeddingsChunk(), 0.85, "эмбеддинги и векторы"))));
        when(facade.chunks(any())).thenReturn(List.of(embeddingsChunk()));
    }

    @AfterEach
    void tearDown() {
        ollama.close();
    }

    private Day29OptimizationService service() {
        return service(new Day29Properties(null, null, null, null, null));
    }

    private Day29OptimizationService service(Day29Properties properties) {
        Day28RagService rag = new Day28RagService(facade,
                new Day28Properties(null, null, null, null, null, null, null),
                localProperties, localLlm, mock(LlmClient.class),
                new LlmProperties(null, null, null));
        return new Day29OptimizationService(rag, localLlm, localProperties, properties);
    }

    private static Day21Chunk embeddingsChunk() {
        return new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                "Эмбеддинги", "Признаковые алгоритмы", "articles/embeddings.md#fixed#0001",
                0, 100, "Эмбеддинги — это векторы, а n-граммы становятся признаками.");
    }

    @Test
    void healthExposesQuantizationContextMemoryAndBothProfiles() {
        Day29HealthResponse health = service().health();

        assertThat(health.model().available()).isTrue();
        assertThat(health.model().installed()).isTrue();
        assertThat(health.model().version()).isEqualTo(Day26MockOllama.VERSION);
        assertThat(health.model().format()).isEqualTo("gguf");
        assertThat(health.model().quantizationLevel()).isEqualTo("Q4_K_M");
        assertThat(health.model().parameterSize()).isEqualTo("3.1B");
        assertThat(health.model().parameterCount()).isEqualTo(3_085_938_688L);
        assertThat(health.model().contextLength()).isEqualTo(32_768L);
        assertThat(health.model().modelSizeBytes()).isPositive();
        assertThat(health.model().loadedMemoryBytes()).isEqualTo(2_047_774_554L);
        assertThat(health.model().reason()).isEmpty();

        assertThat(health.baseline().id()).isEqualTo("baseline");
        assertThat(health.baseline().temperature()).isEqualTo(0.2);
        assertThat(health.baseline().maxTokens()).isEqualTo(300);
        assertThat(health.baseline().numCtx()).isNull();
        assertThat(health.baseline().promptTemplate()).isEqualTo("day28");

        assertThat(health.tuned().id()).isEqualTo("tuned");
        assertThat(health.tuned().temperature()).isEqualTo(0.1);
        assertThat(health.tuned().maxTokens()).isEqualTo(240);
        assertThat(health.tuned().numCtx()).isEqualTo(2048);
        assertThat(health.tuned().promptTemplate()).isEqualTo("compact-rag");

        assertThat(health.benchmarkRuns()).isEqualTo(1);
        assertThat(health.questionsLimit()).isEqualTo(10);
        assertThat(health.retrieval()).contains("без сетевых вызовов");
    }

    @Test
    void healthReportsStoppedServerWithoutThrowing() {
        ollama.close();

        Day29HealthResponse health = service().health();

        assertThat(health.model().available()).isFalse();
        assertThat(health.model().reason()).isNotBlank();
        assertThat(health.model().quantizationLevel()).isEmpty();
    }

    @Test
    void healthReportsMissingModelWithReason() {
        ollama.tagsJson("{\"models\":[]}");

        Day29HealthResponse health = service().health();

        assertThat(health.model().available()).isTrue();
        assertThat(health.model().installed()).isFalse();
        assertThat(health.model().reason()).contains("не установлена");
    }

    @Test
    void askRunsBothProfilesAgainstSameRetrievedPipeline() {
        Day29AskResponse response = service().ask("Что такое эмбеддинги?");

        assertThat(response.sources()).containsExactly("embeddings.md");
        assertThat(response.promptCharsBaseline()).isPositive();
        assertThat(response.promptCharsTuned()).isPositive();
        assertThat(response.promptCharsTuned())
                .isLessThan(response.promptCharsBaseline());

        assertThat(response.baseline().profileId()).isEqualTo("baseline");
        assertThat(response.tuned().profileId()).isEqualTo("tuned");
        assertThat(response.baseline().fallback()).isFalse();
        assertThat(response.tuned().fallback()).isFalse();
        assertThat(response.baseline().answer()).isEqualTo(Day26MockOllama.CYRILLIC_REPLY);
        assertThat(response.tuned().answer()).isEqualTo(Day26MockOllama.CYRILLIC_REPLY);
        assertThat(response.baseline().latencyMs()).isPositive();
        assertThat(response.tuned().latencyMs()).isPositive();
        assertThat(ollama.chatRequests()).isEqualTo(2);

        assertThat(response.qualityVerdict()).contains("Качество");
        assertThat(response.speedVerdict()).contains("Скорость");
    }

    @Test
    void tunedProfileChatSendsOptimizedOptions() {
        service().ask("Что такое эмбеддинги?");

        String body = ollama.lastChatBody();
        assertThat(body).contains("\"num_ctx\":2048")
                .contains("\"temperature\":0.1")
                .contains("\"num_predict\":240");
        assertThat(body).contains("\"role\":\"system\"");
        assertThat(body).contains("нет данных в контексте");
    }

    @Test
    void askFallsBackToIndexWhenServerStopped() {
        ollama.close();

        Day29AskResponse response = service().ask("Что такое эмбеддинги?");

        assertThat(response.baseline().fallback()).isTrue();
        assertThat(response.tuned().fallback()).isTrue();
        assertThat(response.baseline().unavailableReason()).isNotBlank();
        assertThat(response.tuned().unavailableReason()).isNotBlank();
        assertThat(response.baseline().answer()).contains("Запрос:")
                .contains("локальная LLM недоступна");
        assertThat(response.sources()).containsExactly("embeddings.md");
        assertThat(response.qualityVerdict()).contains("Качество");
    }

    @Test
    void askRejectsBlankQuestion() {
        assertThatThrownBy(() -> service().ask("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void runBenchmarksAllQuestionsWithBothProfiles() {
        Day29RunResponse run = service().run();

        assertThat(run.questions()).isEqualTo(10);
        assertThat(run.runs()).isEqualTo(1);
        assertThat(run.baseline().answers()).isEqualTo(10);
        assertThat(run.tuned().answers()).isEqualTo(10);
        assertThat(run.baseline().fallbacks()).isZero();
        assertThat(run.tuned().fallbacks()).isZero();
        assertThat(run.baseline().avgCoveragePercent()).isNotNull();
        assertThat(run.tuned().avgCoveragePercent()).isNotNull();
        assertThat(run.baseline().avgLatencyMs()).isPositive();
        assertThat(run.tuned().avgLatencyMs()).isPositive();
        assertThat(run.loadedMemoryBytes()).isEqualTo(2_047_774_554L);
        assertThat(run.qualityVerdict()).contains("Качество (покрытие ключевых слов)");
        assertThat(run.speedVerdict()).contains("Скорость");
        assertThat(run.resourceVerdict()).contains("Ресурсы");
    }

    @Test
    void runRespectsQuestionsLimit() {
        Day29RunResponse run = service(new Day29Properties(null, 3, null, null, null)).run();

        assertThat(run.questions()).isEqualTo(3);
        assertThat(run.baseline().answers()).isEqualTo(3);
        assertThat(run.tuned().answers()).isEqualTo(3);
    }

    @Test
    void propertiesNormalizeInvalidValuesToDefaults() {
        Day29Properties properties = new Day29Properties(0, 99, -1.0, -5, 0);

        assertThat(properties.benchmarkRuns()).isEqualTo(1);
        assertThat(properties.questionsLimit()).isEqualTo(10);
        assertThat(properties.tunedTemperature()).isEqualTo(0.1);
        assertThat(properties.tunedNumCtx()).isEqualTo(2048);
        assertThat(properties.tunedNumPredict()).isEqualTo(240);
    }
}
