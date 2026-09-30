package com.yunovan.aiadvent.day22;

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

class Day22RagServiceTest {

    private final Day21IndexFacade facade = mock(Day21IndexFacade.class);
    private final LlmClient llm = mock(LlmClient.class);

    private Day22RagService service() {
        return new Day22RagService(facade, new Day22Properties(null, null, null), llm);
    }

    private Day21Chunk chunk() {
        return new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                "Эмбеддинги", "Признаковые алгоритмы", "articles/embeddings.md#fixed#0001",
                0, 100, "Эмбеддинги — это векторы, а n-граммы становятся признаками.");
    }

    private Day21SearchHit hit() {
        return new Day21SearchHit(chunk(), 0.85, "эмбеддинги и векторы");
    }

    private void stubSearch() {
        when(facade.search(any(), any(), any())).thenReturn(new Day21SearchResponse(
                "fixed", "эмбеддинги", 3, List.of(hit())));
        when(facade.chunks(any())).thenReturn(List.of(chunk()));
    }

    @Test
    void questionsReturnsTenControlQuestionsWithExpectations() {
        List<Day22ControlQuestion> questions = service().questions();

        assertThat(questions).hasSize(10);
        for (Day22ControlQuestion question : questions) {
            assertThat(question.id()).isNotBlank();
            assertThat(question.question()).isNotBlank();
            assertThat(question.expectedKeywords()).isNotEmpty();
            assertThat(question.expectedSources()).isNotEmpty();
        }
    }

    @Test
    void ragModePassesQuestionAndContextToLlm() {
        stubSearch();
        when(llm.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Ответ с RAG по эмбеддингам", "stop"));

        Day22AnswerResponse response = service().answer("Что такое эмбеддинги?", "rag");

        ArgumentCaptor<CompletionCommand> captor = ArgumentCaptor.forClass(CompletionCommand.class);
        verify(llm).complete(captor.capture());
        CompletionCommand command = captor.getValue();
        assertThat(command.prompt()).contains("Что такое эмбеддинги?");
        assertThat(command.prompt()).contains("Контекст документов");
        assertThat(command.prompt()).contains("articles/embeddings.md");
        assertThat(command.prompt()).contains("Эмбеддинги — это векторы");
        assertThat(command.systemPrompt()).contains("ТОЛЬКО");

        assertThat(response.mode()).isEqualTo(Day22RagService.MODE_RAG);
        assertThat(response.retrievedHits()).hasSize(1);
        assertThat(response.retrievedHits().get(0).chunk().fileName()).isEqualTo("embeddings.md");
        assertThat(response.fallback()).isFalse();
        assertThat(response.answer()).isEqualTo("Ответ с RAG по эмбеддингам");
    }

    @Test
    void ragModeFallsBackToChunksWhenLlmUnavailable() {
        stubSearch();
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day22AnswerResponse response = service().answer("Что такое эмбеддинги?", "rag");

        assertThat(response.fallback()).isTrue();
        assertThat(response.answer()).contains("Что такое эмбеддинги?");
        assertThat(response.answer()).contains("articles/embeddings.md");
        assertThat(response.answer()).contains("Эмбеддинги — это векторы");
        assertThat(response.retrievedHits()).hasSize(1);
    }

    @Test
    void ragModeWithEmptyResultsStillAnswersFromIndex() {
        when(facade.search(any(), any(), any())).thenReturn(
                new Day21SearchResponse("fixed", "свёкла", 3, List.of()));
        when(facade.chunks(any())).thenReturn(List.of(chunk()));
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day22AnswerResponse response = service().answer("Рецепт борща со свёклой", "rag");

        assertThat(response.fallback()).isTrue();
        assertThat(response.answer()).contains("ничего не найдено");
        assertThat(response.retrievedHits()).isEmpty();
    }

    @Test
    void plainModeSendsOnlyQuestionToLlm() {
        when(llm.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Ответ без RAG", "stop"));

        Day22AnswerResponse response = service().answer("Что такое эмбеддинги?", "plain");

        ArgumentCaptor<CompletionCommand> captor = ArgumentCaptor.forClass(CompletionCommand.class);
        verify(llm).complete(captor.capture());
        CompletionCommand command = captor.getValue();
        assertThat(command.prompt()).isEqualTo("Что такое эмбеддинги?");
        assertThat(command.prompt()).doesNotContain("Контекст документов");
        assertThat(command.prompt()).doesNotContain("embeddings.md");
        assertThat(command.systemPrompt()).contains("без внешнего контекста");

        assertThat(response.mode()).isEqualTo(Day22RagService.MODE_PLAIN);
        assertThat(response.retrievedHits()).isEmpty();
        assertThat(response.fallback()).isFalse();
        assertThat(response.answer()).isEqualTo("Ответ без RAG");
    }

    @Test
    void plainModeFallsBackWithoutSources() {
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day22AnswerResponse response = service().answer("Что такое эмбеддинги?", "plain");

        assertThat(response.fallback()).isTrue();
        assertThat(response.retrievedHits()).isEmpty();
        assertThat(response.answer()).contains("LLM недоступен");
        assertThat(response.answer()).doesNotContain("embeddings.md");
    }

    @Test
    void nullModeDefaultsToRag() {
        stubSearch();
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day22AnswerResponse response = service().answer("Что такое эмбеддинги?", null);

        assertThat(response.mode()).isEqualTo(Day22RagService.MODE_RAG);
        assertThat(response.retrievedHits()).hasSize(1);
    }

    @Test
    void askDetectsPlainModeByMarker() {
        when(llm.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Ответ из своей памяти", "stop"));

        Day22AnswerResponse response = service().ask("Ответь без контекста: что такое эмбеддинги?");

        assertThat(response.mode()).isEqualTo(Day22RagService.MODE_PLAIN);
        ArgumentCaptor<CompletionCommand> captor = ArgumentCaptor.forClass(CompletionCommand.class);
        verify(llm).complete(captor.capture());
        assertThat(captor.getValue().prompt()).doesNotContain("Контекст документов");
    }

    @Test
    void askDefaultsToRag() {
        stubSearch();
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day22AnswerResponse response = service().ask("Что такое эмбеддинги?");

        assertThat(response.mode()).isEqualTo(Day22RagService.MODE_RAG);
        assertThat(response.retrievedHits()).hasSize(1);
    }

    @Test
    void blankQuestionIsRejected() {
        Day22RagService service = service();
        assertThatThrownBy(() -> service.answer("   ", "rag"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустым");
        assertThatThrownBy(() -> service.ask("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустым");
        assertThatThrownBy(() -> service.compare("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустым");
    }

    @Test
    void keywordCoverageCountsExpectedTerms() {
        assertThat(Day22RagService.keywordCoverage(List.of("эмбеддинг", "вектор"),
                "Здесь говорится про эмбеддинги и векторы.")).isEqualTo(1.0);
        assertThat(Day22RagService.keywordCoverage(List.of("эмбеддинг", "вектор"),
                "Только про векторы.")).isEqualTo(0.5);
        assertThat(Day22RagService.keywordCoverage(List.of("эмбеддинг"), "  ")).isZero();
    }

    @Test
    void comparePackagesBothModesAndSources() {
        stubSearch();
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));

        Day22CompareResponse response = service().compare("Что такое эмбеддинги?");

        assertThat(response.rag().mode()).isEqualTo(Day22RagService.MODE_RAG);
        assertThat(response.plain().mode()).isEqualTo(Day22RagService.MODE_PLAIN);
        assertThat(response.retrievedSources()).contains("embeddings.md");
        assertThat(response.verdict()).contains("embeddings.md");
    }

    @Test
    void healthExposesConfiguration() {
        when(facade.health()).thenReturn(new Day21HealthResponse(
                "index", "0.1.0", 9, 131000, 44,
                List.of(new Day21StrategyInfo("fixed", "описание", true, 100))));
        Day22RagService service = new Day22RagService(facade,
                new Day22Properties(null, 3, 400), llm);

        Day22HealthResponse health = service.health();

        assertThat(health.documents()).isEqualTo(9);
        assertThat(health.strategy()).isEqualTo("fixed");
        assertThat(health.topK()).isEqualTo(3);
        assertThat(health.answerMaxTokens()).isEqualTo(400);
    }
}