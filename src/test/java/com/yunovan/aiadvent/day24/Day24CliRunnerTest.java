package com.yunovan.aiadvent.day24;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21IndexException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day24CliRunnerTest {

    private final Day24Service service = mock(Day24Service.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day24CliRunner runner = new Day24CliRunner(service, props(), context);

    private static Day24Properties props() {
        return new Day24Properties(null, null, null, null, null, null, null, null, null, null);
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

    private Day24GroundedResponse known() {
        return new Day24GroundedResponse("Что такое эмбеддинги документов?",
                "Что такое эмбеддинги? вектор признак n-грамм документ", true, 10, 2,
                List.of(new Day24Source("articles/embeddings.md", "Признаковые алгоритмы",
                        "articles/embeddings.md#fixed#0001", 0.9, "сниппет")),
                List.of(new Day24Quote("articles/embeddings.md", "Признаковые алгоритмы",
                        "articles/embeddings.md#fixed#0001",
                        "Эмбеддинги — это векторы, а n-граммы становятся признаками.",
                        0.9, 2)),
                "В документах говорится: «Эмбеддинги — это векторы…»", 0.9, 100.0,
                true, false, true);
    }

    private Day24GroundedResponse unknown() {
        return new Day24GroundedResponse("Какая сегодня погода в Москве?",
                "Какая сегодня погода в Москве?", false, 10, 10,
                List.of(), List.of(),
                "Не знаю. В найденном контексте недостаточно информации…", 0.1, 0.0,
                false, true, true);
    }

    @Test
    void otherDayTriggersNoInteractions() {
        runner.run(new DefaultApplicationArguments("--day=22", "--check"));

        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsPipelineConfiguration() {
        when(service.health()).thenReturn(new Day24HealthResponse(9, 131000, 44, "fixed",
                10, 5, 0.5, 0.32, true, 3, 24, 0.35, 400,
                List.of("Загадай число от одного до ста"),
                List.of("fixed")));

        String output = run("--day=24", "--check");

        assertThat(output)
                .contains("ЦИТАТЫ, ИСТОЧНИКИ И АНТИ-ГАЛЛЮЦИНАЦИИ")
                .contains("документов в корпусе: 9")
                .contains("топ-K до/после: 10/5")
                .contains("порог фильтра: 0.50")
                .contains("порог «не знаю»: 0.32")
                .contains("цитат на источник: 3")
                .contains("поддержка ответа цитатами ≥ 35%")
                .contains("слабые вопросы для проверки «не знаю»: Загадай число от одного до ста");
    }

    @Test
    void answerPrintsSourcesQuotesAndSupport() {
        when(service.answer(any())).thenReturn(known());

        String output = run("--day=24", "--answer=Что такое эмбеддинги документов?");

        assertThat(output)
                .contains("ОТВЕТ С ИСТОЧНИКАМИ И ЦИТАТАМИ")
                .contains("запрос для поиска: Что такое эмбеддинги? вектор")
                .contains("переписан")
                .contains("кандидатов до фильтрации: 10")
                .contains("отсеяно: 2")
                .contains("- articles/embeddings.md | раздел «Признаковые алгоритмы» | чанк")
                .contains("«Эмбеддинги — это векторы, а n-граммы становятся признаками.»")
                .contains("поддержка ответа цитатами: 100.0%")
                .contains("подтверждён: да")
                .contains("режим «не знаю»: нет")
                .contains("ответ:");
    }

    @Test
    void unknownFlagIsPrintedWhenEvidenceIsWeak() {
        when(service.answer(any())).thenReturn(unknown());

        String output = run("--day=24", "--unknown");

        assertThat(output)
                .contains("лучший score: 0.100")
                .contains("НИЖЕ порога «не знаю»")
                .contains("режим «не знаю»: ДА")
                .contains("Не знаю. В найденном контексте");
    }

    @Test
    void questionsPrintsTenWithExpectations() {
        when(service.questions()).thenReturn(Day24ControlQuestions.ALL);

        String output = run("--day=24", "--questions");

        assertThat(output)
                .contains("КОНТРОЛЬНЫЕ ВОПРОСЫ (10)")
                .contains("q01")
                .contains("источники: embeddings.md");
    }

    @Test
    void evaluatePrintsComplianceAndAntiHallucination() {
        when(service.evaluate()).thenReturn(new Day24EvalResponse(10, 10, 10, 10, 95.0,
                2, 2, "Комплаентность на 10 вопросах: источники 10/10, цитаты 10/10, "
                + "смысл ответа подтверждён цитатами 10/10 (средняя поддержка 95.0%). "
                + "Анти-галлюцинация: режим «не знаю» сработал на 2/2 слабых вопросах.",
                List.of(new Day24EvalItem("q01", "Что такое эмбеддинги?",
                        true, 1, true, 2, true, 100.0, false, List.of("embeddings.md"))),
                List.of(new Day24EvalWeakItem("w01", "Загадай число от одного до ста",
                        true, 0.1, List.of()))));

        String output = run("--day=24", "--evaluate");

        assertThat(output)
                .contains("КОМПЛАЕНТНОСТЬ НА 10 ВОПРОСАХ + АНТИ-ГАЛЛЮЦИНАЦИЯ")
                .contains("источники в каждом ответе: 10/10")
                .contains("цитаты в каждом ответе: 10/10")
                .contains("смысл ответа подтверждён цитатами: 10/10")
                .contains("средняя поддержка 95.0%")
                .contains("режим «не знаю» на слабых вопросах: 2/2")
                .contains("Вердикт: Комплаентность на 10 вопросах")
                .contains("«не знаю»: да");
    }

    @Test
    void indexFailurePrintsErrorMessage() {
        when(service.health())
                .thenThrow(new Day21IndexException("Не удалось прочитать индекс"));

        String output = run("--day=24", "--check");

        assertThat(output).contains("ОШИБКА").contains("Не удалось прочитать индекс");
    }
}