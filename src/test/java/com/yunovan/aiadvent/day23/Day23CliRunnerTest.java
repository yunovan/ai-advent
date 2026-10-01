package com.yunovan.aiadvent.day23;

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

class Day23CliRunnerTest {

    private final Day23RagService service = mock(Day23RagService.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day23CliRunner runner = new Day23CliRunner(service, props(), context);

    private static Day23Properties props() {
        return new Day23Properties(null, null, null, null, null, null);
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
        runner.run(new DefaultApplicationArguments("--day=22", "--check"));

        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsPipelineConfiguration() {
        when(service.health()).thenReturn(new Day23HealthResponse(9, 131000, 44, "fixed",
                10, 5, 0.5, true, 400,
                List.of(new Day21StrategyInfo("fixed", "описание", true, 100))));

        String output = run("--day=23", "--check");

        assertThat(output)
                .contains("RAG / РЕРАНКИНГ И ФИЛЬТРАЦИЯ")
                .contains("документов в корпусе: 9")
                .contains("топ-K до фильтрации: 10")
                .contains("после: 5")
                .contains("порог отсечения score: 0.50")
                .contains("query rewrite включён: да");
    }

    @Test
    void rewritePrintsOriginalAndExpansion() {
        when(service.rewrite(any())).thenReturn(new Day23RewriteResponse(
                "Что такое эмбеддинги?", "Что такое эмбеддинги? вектор признак n-грамм документ",
                true, List.of("эмбеддинг вектор признак n-грамм документ")));

        String output = run("--day=23", "--rewrite=Что такое эмбеддинги?");

        assertThat(output)
                .contains("ПЕРЕПИСЫВАНИЕ ЗАПРОСА")
                .contains("исходный запрос: Что такое эмбеддинги?")
                .contains("переписанный: Что такое эмбеддинги? вектор")
                .contains("применено правил: 1")
                .contains("+ «эмбеддинг вектор признак n-грамм документ»");
    }

    @Test
    void answerPrintsPipelineStatsAndAnswer() {
        when(service.answer(any(), any())).thenReturn(new Day23AnswerResponse(
                "Что такое эмбеддинги?", "full",
                "Что такое эмбеддинги? вектор признак n-грамм документ", true,
                10, 5, List.of(hit()), "Эмбеддинги — это векторы.", false));

        String output = run("--day=23", "--answer=Что такое эмбеддинги?", "--mode=full");

        assertThat(output)
                .contains("ОТВЕТ АГЕНТА (РЕЖИМ FULL)")
                .contains("переписан")
                .contains("кандидатов до фильтрации: 10")
                .contains("отсеяно: 5")
                .contains("источники: embeddings.md")
                .contains("Эмбеддинги — это векторы.");
    }

    @Test
    void comparePrintsBothModesAndVerdict() {
        when(service.compare(any(), any(), any())).thenReturn(new Day23CompareResponse(
                "Что такое эмбеддинги?",
                new Day23AnswerResponse("Что такое эмбеддинги?", "base",
                        "Что такое эмбеддинги?", false, 10, 0,
                        List.of(hit()), "Ответ без фильтра", true),
                new Day23AnswerResponse("Что такое эмбеддинги?", "full",
                        "Что такое эмбеддинги?", true, 10, 7,
                        List.of(hit()), "Ответ с фильтром", true),
                List.of("embeddings.md"), List.of("embeddings.md"),
                "Фильтр отсеял нерелевантное."));

        String output = run("--day=23", "--compare=Что такое эмбеддинги?");

        assertThat(output)
                .contains("СРАВНЕНИЕ РЕЖИМОВ: BASE / FULL")
                .contains("источники после: embeddings.md")
                .contains("Ответ без фильтра")
                .contains("Ответ с фильтром")
                .contains("Вердикт: Фильтр отсеял нерелевантное.");
    }

    @Test
    void questionsPrintsTenWithExpectations() {
        when(service.questions()).thenReturn(Day23ControlQuestions.ALL);

        String output = run("--day=23", "--questions");

        assertThat(output)
                .contains("КОНТРОЛЬНЫЕ ВОПРОСЫ (10)")
                .contains("q01")
                .contains("ожидание (ключевые слова): эмбеддинг, вектор, n-грамм")
                .contains("источники: embeddings.md");
    }

    @Test
    void evaluatePrintsMetricsAndVerdict() {
        when(service.evaluate()).thenReturn(new Day23EvalResponse(10, 9, 10, 90.0, 100.0, 23,
                60.0, 75.0, 15.0, 8, "Фильтр работает", List.of(
                        new Day23EvalItem("q01", "Что такое эмбеддинги?",
                                List.of("эмбеддинг"), List.of("embeddings.md"),
                                List.of("embeddings.md"), true, 100.0, 9,
                                List.of("embeddings.md"), true, 100.0, 2))));

        String output = run("--day=23", "--evaluate");

        assertThat(output)
                .contains("КАЧЕСТВО НА 10 КОНТРОЛЬНЫХ ВОПРОСАХ")
                .contains("recall источников: base 9/10 = 90.0%")
                .contains("10/10 = 100.0%")
                .contains("отсеяно кандидатов фильтром всего: 23")
                .contains("среднее покрытие ключевых слов: base 60.0%")
                .contains("full 75.0%")
                .contains("Вердикт: Фильтр работает");
    }

    @Test
    void promptAutoRunsAgent() {
        when(service.ask(any())).thenReturn(new Day23AnswerResponse(
                "Что такое эмбеддинги?", "full",
                "Что такое эмбеддинги?", true, 10, 6,
                List.of(hit()), "Эмбеддинги — это векторы.", true));

        String output = run("--day=23", "--prompt=Что такое эмбеддинги?");

        assertThat(output)
                .contains("ОТВЕТ АГЕНТА (РЕЖИМ FULL)")
                .contains("источники: embeddings.md")
                .contains("Эмбеддинги — это векторы.");
    }

    @Test
    void indexFailurePrintsErrorMessage() {
        when(service.health())
                .thenThrow(new Day21IndexException("Не удалось прочитать индекс"));

        String output = run("--day=23", "--check");

        assertThat(output).contains("ОШИБКА").contains("Не удалось прочитать индекс");
    }
}