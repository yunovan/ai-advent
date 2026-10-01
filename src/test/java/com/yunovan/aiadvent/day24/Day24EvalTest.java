package com.yunovan.aiadvent.day24;

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

class Day24EvalTest {

    @TempDir
    Path tempDir;

    private Day24Service service;

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
        LlmClient llm = mock(LlmClient.class);
        when(llm.complete(any(CompletionCommand.class)))
                .thenThrow(new LlmException("offline"));
        service = new Day24Service(facade, new Day24Properties(null, null, null, null, null,
                null, null, null, null, null), llm);
    }

    @Test
    void eachControlQuestionReturnsSourcesAndQuotesAndSupportedAnswer() {
        for (Day24ControlQuestion question : Day24ControlQuestions.ALL) {
            Day24GroundedResponse response = service.answer(question.question());

            assertThat(response.sources())
                    .as(question.id() + " источники")
                    .isNotEmpty();
            assertThat(response.quotes())
                    .as(question.id() + " цитаты")
                    .isNotEmpty();
            assertThat(response.supported())
                    .as(question.id() + " поддержка ответа цитатами")
                    .isTrue();
            assertThat(response.unknown())
                    .as(question.id() + " не должен активировать «не знаю»")
                    .isFalse();
        }
    }

    @Test
    void embeddingsQuestionSourcesContainExpectedDocument() {
        Day24GroundedResponse response = service.answer(
                "Что такое эмбеддинги документов и как они получаются?");

        List<String> sourceNames = response.sources().stream().map(Day24Source::source).toList();
        assertThat(sourceNames).contains("articles/embeddings.md");
        assertThat(response.quotes().stream().map(Day24Quote::source))
                .contains("articles/embeddings.md");
        for (Day24Quote quote : response.quotes()) {
            assertThat(sourceNames).as("цитата из источника, найденного в выдаче")
                    .contains(quote.source());
        }
    }

    @Test
    void weakQuestionsForceUnknownModeWithClarification() {
        for (Day24WeakQuestion weak : Day24ControlQuestions.WEAK) {
            Day24GroundedResponse response = service.answer(weak.question());

            assertThat(response.unknown())
                    .as(weak.id() + " должен активировать «не знаю»")
                    .isTrue();
            assertThat(response.answer()).containsIgnoringCase("не знаю");
            assertThat(response.answer()).containsIgnoringCase("уточните");
            assertThat(response.supported()).isFalse();
        }
    }

    @Test
    void evaluateReportsFullComplianceAndAntiHallucination() {
        Day24EvalResponse evaluate = service.evaluate();

        assertThat(evaluate.knownCount()).isEqualTo(10);
        assertThat(evaluate.sourcesPresent()).isEqualTo(10);
        assertThat(evaluate.quotesPresent()).isEqualTo(10);
        assertThat(evaluate.supportedCount()).isEqualTo(10);
        assertThat(evaluate.avgSupportPercent()).isGreaterThan(50.0);
        assertThat(evaluate.unknownTriggered()).isEqualTo(2);
        assertThat(evaluate.verdict()).contains("Анти-галлюцинация");
        assertThat(evaluate.verdict()).contains("источники 10/10");
    }

    @Test
    void quotesAreVerbatimChunkTextForControlQuestions() {
        for (Day24ControlQuestion question : Day24ControlQuestions.ALL) {
            Day24GroundedResponse response = service.answer(question.question());
            for (Day24Quote quote : response.quotes()) {
                assertThat(response.sources().stream().map(Day24Source::chunkId))
                        .as(question.id() + " цитата из найденного чанка")
                        .contains(quote.chunkId());
            }
        }
    }
}