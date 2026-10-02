package com.yunovan.aiadvent.day25;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21IndexException;
import com.yunovan.aiadvent.day24.Day24Quote;
import com.yunovan.aiadvent.day24.Day24Source;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day25CliRunnerTest {

    private final Day25ChatService service = mock(Day25ChatService.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day25CliRunner runner = new Day25CliRunner(service, props(), context);

    private static Day25Properties props() {
        return new Day25Properties(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null);
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

    private Day25ChatTurn turn() {
        return new Day25ChatTurn("cli", 1, "Что такое эмбеддинги документов?",
                "В документах говорится: «Эмбеддинги — это векторы…»",
                "Что такое эмбеддинги документов? эмбеддинг документ", true, 10, 2, 0.9,
                List.of(new Day24Source("articles/embeddings.md", "Признаковые алгоритмы",
                        "articles/embeddings.md#fixed#0001", 0.9, "сниппет")),
                List.of(new Day24Quote("articles/embeddings.md", "Признаковые алгоритмы",
                        "articles/embeddings.md#fixed#0001",
                        "Эмбеддинги — это векторы, а n-граммы становятся признаками.", 0.9, 2)),
                100.0, true, false, true,
                new Day25TaskMemory("разобраться в эмбеддингах", 1, 1,
                        List.of("только по документации"), List.of("без выдумок"),
                        List.of("эмбеддинг")),
                List.of(new Day25Message("user", "Что такое эмбеддинги документов?", 1)),
                2);
    }

    private void stubMemory() {
        when(service.memory(any())).thenReturn(new Day25SessionView("cli", 1, 2,
                "разобраться в эмбеддингах", "ассистент ответил", 1, 1, 1));
        when(service.taskMemory(any())).thenReturn(new Day25TaskMemory(
                "разобраться в эмбеддингах", 1, 1, List.of("только по документации"),
                List.of("без выдумок"), List.of("эмбеддинг")));
    }

    @Test
    void otherDayTriggersNoInteractions() {
        runner.run(new DefaultApplicationArguments("--day=23", "--check"));

        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsChatAndMemoryConfiguration() {
        when(service.health()).thenReturn(new Day25HealthResponse(9, 131000, 44, "fixed",
                10, 5, 0.5, 0.32, true, 3, 24, 0.35, 400, 40, 200, 8, 1,
                List.of("Эмбеддинги: от вектора до признаков"), List.of("fixed")));

        String out = run("--day=25", "--check");

        assertThat(out)
                .contains("МИНИ-ЧАТ С RAG И ПАМЯТЬЮ ЗАДАЧИ")
                .contains("документов в корпусе: 9")
                .contains("история диалога: до 40 сообщений")
                .contains("терминов в памяти задачи: 8")
                .contains("Эмбеддинги: от вектора до признаков");
    }

    @Test
    void sayPrintsReplySourcesAndMemory() {
        when(service.chat(any(), any())).thenReturn(turn());

        String out = run("--day=25", "--say=Что такое эмбеддинги документов?");

        assertThat(out)
                .contains("[cli #1] вы: Что такое эмбеддинги документов?")
                .contains("ассистент: В документах говорится")
                .contains("источники: 1 шт.")
                .contains("articles/embeddings.md")
                .contains("чанк articles/embeddings.md#fixed#0001")
                .contains("цитаты: 1 шт.")
                .contains("память задачи: цель «разобраться в эмбеддингах»");
    }

    @Test
    void interactiveChatKeepsMemoryAcrossTurns() {
        when(service.chat(any(), any())).thenReturn(turn());
        stubMemory();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.runRepl("cli", new BufferedReader(new StringReader(
                    "Моя цель: разобраться в эмбеддингах\n"
                            + "Что такое эмбеддинги документов?\n"
                            + "\n"
                            + "выход\n")));
        } finally {
            System.setOut(original);
        }
        String out = buffer.toString();

        assertThat(out)
                .contains("вы> ")
                .contains("ассистент: В документах говорится")
                .contains("память задачи: цель «разобраться в эмбеддингах»")
                .contains("=== ПАМЯТЬ ЗАДАЧИ [cli] ===");
    }

    @Test
    void evaluatePrintsGoalRetentionAndSources() {
        when(service.evaluate()).thenReturn(new Day25EvalResponse(2, 25, 23, 23, 23,
                25, 25, 2, 95.5, "Мини-чат на 2 длинных сценариях, 25 сообщений: …",
                List.of(new Day25ScenarioResult("s01", "Эмбеддинги", "цель", 12, 11, 11, 11,
                        12, 12, 96.0, 1, new Day25TaskMemory("цель", 12, 12, List.of(),
                                List.of(), List.of("эмбеддинг")),
                        List.of(new Day25ScenarioTurn(1, "Что такое эмбеддинги?", true, 2, 2,
                                true, false, true, true, 0.9,
                                List.of("articles/embeddings.md")))))));

        String out = run("--day=25", "--evaluate");

        assertThat(out)
                .contains("ДЛИННЫЕ СЦЕНАРИИ: ПАМЯТЬ ЗАДАЧИ + ИСТОЧНИКИ")
                .contains("сценариев: 2 | сообщений: 25")
                .contains("ответы с источниками: 23/25")
                .contains("цель диалога удержана: 25/25")
                .contains("ограничения удержаны: 25/25")
                .contains("Вердикт: Мини-чат на 2 длинных сценариях");
    }

    @Test
    void scenariosPrintsBothLongDialogs() {
        when(service.scenarios()).thenReturn(Day25Scenarios.ALL);

        String out = run("--day=25", "--scenarios");

        assertThat(out)
                .contains("СЦЕНАРИИ ДИАЛОГОВ (2)")
                .contains("s01 | Эмбеддинги: от вектора до признаков")
                .contains("s02 | MCP-сервер и память агента")
                .contains("сообщений: 12")
                .contains("сообщений: 13");
    }

    @Test
    void historyPrintsMemoryTask() {
        when(service.memory("demo")).thenReturn(new Day25SessionView("demo", 1, 2,
                "разобраться в эмбеддингах", "ассистент ответил", 1, 1, 1));
        when(service.taskMemory("demo")).thenReturn(new Day25TaskMemory(
                "разобраться в эмбеддингах", 1, 1, List.of("только по документации"),
                List.of("без выдумок"), List.of("эмбеддинг")));

        String out = run("--day=25", "--history", "--session=demo");

        assertThat(out)
                .contains("=== ПАМЯТЬ ЗАДАЧИ [demo] ===")
                .contains("цель: разобраться в эмбеддингах")
                .contains("ограничения: без выдумок")
                .contains("термины: эмбеддинг");
    }

    @Test
    void indexFailureIsReported() {
        when(service.health()).thenThrow(new Day21IndexException("Индекс не построен"));

        String out = run("--day=25", "--check");

        assertThat(out).contains("ОШИБКА: Индекс не построен");
    }

    @Test
    void blankSayIsRejectedWithErrorMessage() {
        when(service.chat(any(), any()))
                .thenThrow(new IllegalArgumentException("Сообщение не может быть пустым"));

        String out = run("--day=25", "--say=вопрос");

        assertThat(out).contains("ОШИБКА: Сообщение не может быть пустым");
    }
}
