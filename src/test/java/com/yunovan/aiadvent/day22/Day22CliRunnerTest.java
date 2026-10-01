package com.yunovan.aiadvent.day22;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21IndexException;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day21.Day21StrategyInfo;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day22CliRunnerTest {

    private final Day22RagService service = mock(Day22RagService.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day22CliRunner runner = new Day22CliRunner(service, props(), context);

    private static Day22Properties props() {
        return new Day22Properties(null, null, null);
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

    private Day21SearchHit hit() {
        return new Day21SearchHit(
                new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                        "Эмбеддинги", "Признаковые алгоритмы",
                        "articles/embeddings.md#fixed#0001", 0, 100, "текст"),
                0.8, "сниппет");
    }

    @Test
    void otherDayTriggersNoInteractions() {
        runner.run(new DefaultApplicationArguments("--day=21", "--check"));

        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsCorpusSummary() {
        when(service.health()).thenReturn(new Day22HealthResponse(9, 131000, 44, "fixed", 3, 400,
                List.of(new Day21StrategyInfo("fixed", "описание", true, 100))));

        String output = run("--day=22", "--check");

        assertThat(output)
                .contains("RAG / ИНДЕКС")
                .contains("документов в корпусе: 9")
                .contains("стратегия поиска: fixed")
                .contains("top-K: 3");
    }

    @Test
    void answerPrintsAnswerAndSources() {
        when(service.answer(any(), any())).thenReturn(new Day22AnswerResponse(
                "Что такое эмбеддинги?", "rag", List.of(hit()),
                "Эмбеддинги — это векторы.", false));

        String output = run("--day=22", "--answer=Что такое эмбеддинги?");

        assertThat(output)
                .contains("ОТВЕТ АГЕНТА (RAG)")
                .contains("источники: embeddings.md")
                .contains("fallback (LLM недоступен): нет")
                .contains("Эмбеддинги — это векторы.");
    }

    @Test
    void comparePrintsBothModes() {
        when(service.compare(any())).thenReturn(new Day22CompareResponse(
                "Что такое эмбеддинги?",
                new Day22AnswerResponse("Что такое эмбеддинги?", "rag", List.of(hit()),
                        "Ответ с контекстом", false),
                new Day22AnswerResponse("Что такое эмбеддинги?", "plain", List.of(),
                        "Ответ без контекста", true),
                List.of("embeddings.md"), "С RAG модель получила контекст."));

        String output = run("--day=22", "--compare=Что такое эмбеддинги?");

        assertThat(output)
                .contains("СРАВНЕНИЕ: С RAG / БЕЗ RAG")
                .contains("С RAG — источники: embeddings.md")
                .contains("Ответ с контекстом")
                .contains("БЕЗ RAG — ответ")
                .contains("Ответ без контекста")
                .contains("Вердикт: С RAG модель получила контекст.");
    }

    @Test
    void questionsPrintsTenWithExpectations() {
        when(service.questions()).thenReturn(Day22ControlQuestions.ALL);

        String output = run("--day=22", "--questions");

        assertThat(output)
                .contains("КОНТРОЛЬНЫЕ ВОПРОСЫ (10)")
                .contains("q01")
                .contains("ожидание (ключевые слова): эмбеддинг, вектор, n-грамм")
                .contains("источники: embeddings.md");
    }

    @Test
    void evaluatePrintsMetricsAndVerdict() {
        when(service.evaluate()).thenReturn(new Day22EvalResponse(10, 8, 80.0, 70.0, 5.0, 65.0,
                "Вердикт RAG", List.of(
                        new Day22EvalItem("q01", "Что такое эмбеддинги?", List.of("эмбеддинг"),
                                List.of("embeddings.md"), List.of("embeddings.md"), true,
                                100.0, 0.0, 100.0))));

        String output = run("--day=22", "--evaluate");

        assertThat(output)
                .contains("КАЧЕСТВО НА 10 КОНТРОЛЬНЫХ ВОПРОСАХ")
                .contains("8/10")
                .contains("80.0%")
                .contains("с RAG 70.0%")
                .contains("без RAG 5.0%")
                .contains("Вердикт: Вердикт RAG")
                .contains("покрытие: RAG 100.0%");
    }

    @Test
    void promptAutoRunsAgent() {
        when(service.ask(any())).thenReturn(new Day22AnswerResponse(
                "Что такое эмбеддинги?", "rag", List.of(hit()),
                "Эмбеддинги — это векторы.", true));

        String output = run("--day=22", "--prompt=Что такое эмбеддинги?");

        assertThat(output)
                .contains("ОТВЕТ АГЕНТА (RAG)")
                .contains("источники: embeddings.md")
                .contains("Эмбеддинги — это векторы.");
    }

    @Test
    void indexFailurePrintsErrorMessage() {
        when(service.health())
                .thenThrow(new Day21IndexException("Не удалось прочитать индекс"));

        String output = run("--day=22", "--check");

        assertThat(output).contains("ОШИБКА").contains("Не удалось прочитать индекс");
    }
}