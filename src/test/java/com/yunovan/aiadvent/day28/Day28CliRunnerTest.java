package com.yunovan.aiadvent.day28;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day28CliRunnerTest {

    private final Day28RagService service = mock(Day28RagService.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day28CliRunner runner = new Day28CliRunner(service, context);

    private String run(String... args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.run(new DefaultApplicationArguments(args));
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static Day28HealthResponse running() {
        return new Day28HealthResponse(9, 131000, 44, "fixed", 10, 5, 0.5, true, 400, 2,
                "локальный: n-gram эмбеддинги + cosine + BM25-переранжирование, без сетевых вызовов",
                "http://localhost:11434", "qwen2.5:3b", true, "0.35.1", true,
                true, "openai/gpt-4o-mini", false, "");
    }

    private static Day21Chunk chunk() {
        return new Day21Chunk("fixed", "articles/embeddings.md", "embeddings.md",
                "Эмбеддинги", "Признаковые алгоритмы", "articles/embeddings.md#fixed#0001",
                0, 100, "Эмбеддинги — это векторы.");
    }

    private static Day28AnswerResponse answer() {
        return new Day28AnswerResponse("Что такое эмбеддинги?", "local", "qwen2.5:3b",
                "эмбеддинги вектор", true, 2, 1,
                List.of(new Day21SearchHit(chunk(), 1.0, "эмбеддинги и векторы")),
                List.of("embeddings.md"), "Ответ локальной модели", false, 87.5,
                3_100L, 45, 25, 5.3, "");
    }

    private static Day28CompareResponse compare() {
        return new Day28CompareResponse("Что такое эмбеддинги?", answer(),
                new Day28AnswerResponse("Что такое эмбеддинги?", "cloud",
                        "openai/gpt-4o-mini", "эмбеддинги вектор", true, 2, 1,
                        List.of(), List.of("embeddings.md"), "Облачный ответ", false, 60.0,
                        1_000L, 45, 25, 25.0, ""),
                true, List.of("embeddings.md"),
                "Один и тот же локальный контекст из 1 источников (embeddings.md). "
                        + "Опора на контекст: локальная 87.5% против облачной 60.0%. "
                        + "Быстрее — локальная модель (разница 2100 мс).");
    }

    private static Day28EvalResponse evaluate() {
        return new Day28EvalResponse(10, 2, 1, 10.0,
                new Day28EngineStats(true, "", 20, 0, 6.7, 0.0, 3_100.0, 120.0, 5.3),
                new Day28EngineStats(true, "", 20, 0, 6.7, 1.4, 1_000.0, 50.0, 25.0),
                "Качество (покрытие ключевых слов): локальная модель 6.7% против облачной 6.7%.",
                "Средняя задержка: локальная 3100 мс против облачной 1000 мс. "
                        + "Быстрее — облачная модель (разница 2100 мс).",
                "Отклонение покрытия между повторами: локальная ±0.0 п.п. — ответы стабильны.",
                List.of(new Day28EvalItem("q01", "Что такое эмбеддинги?",
                        List.of("эмбеддинг"), List.of("embeddings.md"),
                        List.of("embeddings.md"), true,
                        List.of(100.0, 100.0), List.of(3_100L, 3_100L),
                        List.of(100.0, 100.0), List.of(1_000L, 1_000L))));
    }

    @Test
    void otherDayTriggersNoInteractions() {
        runner.run(new DefaultApplicationArguments("--day=27", "--check"));

        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsIndexRetrievalLocalServerAndCloudKey() {
        when(service.health()).thenReturn(running());

        String out = run("--day=28", "--check");

        assertThat(out).contains("=== Локальная RAG-система (день 28) ===")
                .contains("Индекс: 9 документов | 131000 символов | ~44 страниц")
                .contains("Поиск: локальный: n-gram эмбеддинги")
                .contains("без сетевых вызовов")
                .contains("Стратегия: fixed (top-k 10→5, порог 0.50, rewrite: да, "
                        + "макс. токенов: 400)")
                .contains("Локальная LLM: http://localhost:11434 / qwen2.5:3b")
                .contains("Сервер запущен: да (версия 0.35.1) | модель установлена: да")
                .contains("Облачное сравнение: ключ задан, модель openai/gpt-4o-mini")
                .contains("Облачная генерация в --ask: нет (только локальная модель)")
                .contains("Повторов в --evaluate: 2");
    }

    @Test
    void checkPrintsStoppedServerAndMissingCloudKey() {
        when(service.health()).thenReturn(new Day28HealthResponse(9, 131000, 44, "fixed",
                10, 5, 0.5, true, 400, 2, "локальный поиск",
                "http://localhost:11434", "qwen2.5:3b", false, "", false,
                false, "", false,
                "Локальный LLM недоступен на http://localhost:11434 (connection refused)"));

        String out = run("--day=28", "--check");

        assertThat(out).contains("Сервер запущен: нет")
                .contains("Причина: Локальный LLM недоступен на http://localhost:11434")
                .contains("Облачное сравнение: ключ не задан — --compare покажет "
                        + "только локальный ответ");
    }

    @Test
    void askPrintsLocalAnswerWithSharedMetrics() {
        when(service.ask("Что такое эмбеддинги?")).thenReturn(answer());

        String out = run("--day=28", "--ask=Что такое эмбеддинги?");

        assertThat(out).contains("=== Локальный ответ (qwen2.5:3b) ===")
                .contains("Вопрос: Что такое эмбеддинги?")
                .contains("Поисковый запрос: эмбеддинги вектор")
                .contains("Источники: embeddings.md")
                .contains("Ответ: Ответ локальной модели")
                .contains("Метрики: задержка 3100 мс | токены 45 вход / 25 выход | 5.3 ток/с")
                .contains("опора на контекст 87.5%");
    }

    @Test
    void askPrintsFallbackNoticeWhenLocalModelIsDown() {
        when(service.ask("Что такое эмбеддинги?")).thenReturn(
                new Day28AnswerResponse("Что такое эмбеддинги?", "local", "qwen2.5:3b",
                        "эмбеддинги вектор", true, 2, 1, List.of(),
                        List.of("embeddings.md"),
                        "Запрос: Что такое эмбеддинги? ...", true, 87.5,
                        2L, 45, 25, 5.3,
                        "Локальный LLM недоступен на http://localhost:11434"));

        String out = run("--day=28", "--ask=Что такое эмбеддинги?");

        assertThat(out).contains("Фолбэк: да — локальная LLM не ответила: "
                + "Локальный LLM недоступен на http://localhost:11434");
    }

    @Test
    void askPrintsNoSourcesNoticeWhenNothingRetrieved() {
        when(service.ask("Пусто")).thenReturn(
                new Day28AnswerResponse("Пусто", "local", "qwen2.5:3b",
                        "пусто", false, 0, 0, List.of(), List.of(),
                        "Ничего не найдено", true, null,
                        2L, 45, 25, 5.3, "Локальный LLM недоступен"));

        String out = run("--day=28", "--ask=Пусто");

        assertThat(out).contains("Источники: ничего не найдено")
                .contains("Метрики: задержка 2 мс | токены 45 вход / 25 выход | 5.3 ток/с")
                .doesNotContain("опора на контекст");
    }

    @Test
    void blankAskPrintsValidationError() {
        when(service.ask(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("Вопрос не может быть пустым"));

        String out = run("--day=28", "--ask=");

        assertThat(out).contains("ОШИБКА: Вопрос не может быть пустым");
    }

    @Test
    void llmFailurePrintsErrorLine() {
        when(service.ask("Вопрос")).thenThrow(
                new com.yunovan.aiadvent.day26.Day26LlmException(
                        "Локальный LLM недоступен на http://localhost:11434"));

        String out = run("--day=28", "--ask=Вопрос");

        assertThat(out).contains("ОШИБКА: Локальный LLM недоступен");
    }

    @Test
    void comparePrintsBothSidesAndVerdict() {
        when(service.compare("Что такое эмбеддинги?")).thenReturn(compare());

        String out = run("--day=28", "--compare=Что такое эмбеддинги?");

        assertThat(out).contains("=== Сравнение: локальная против облачной ===")
                .contains("Вопрос: Что такое эмбеддинги?")
                .contains("Общий контекст: embeddings.md")
                .contains("--- Локальная (qwen2.5:3b) ---")
                .contains("Ответ: Ответ локальной модели")
                .contains("--- Облачная (openai/gpt-4o-mini) ---")
                .contains("Ответ: Облачный ответ")
                .contains("Вердикт: Один и тот же локальный контекст из 1 источников "
                        + "(embeddings.md).")
                .contains("Быстрее — локальная модель (разница 2100 мс)");
    }

    @Test
    void comparePrintsCloudUnavailableNotice() {
        when(service.compare("Вопрос")).thenReturn(new Day28CompareResponse("Вопрос",
                answer(),
                new Day28AnswerResponse("Вопрос", "cloud", "", "", false, 0, 0,
                        List.of(), List.of(), "", false, null, 0L, 0, 0, 0.0,
                        "облачный ключ не задан (LLM_API_KEY)"),
                false, List.of("embeddings.md"),
                "Сравнить не с чем: облачная модель недоступна — "
                        + "облачный ключ не задан (LLM_API_KEY). Локальная сторона выше."));

        String out = run("--day=28", "--compare=Вопрос");

        assertThat(out).contains("Недоступна: облачный ключ не задан (LLM_API_KEY)")
                .contains("Вердикт: Сравнить не с чем: облачная модель недоступна");
    }

    @Test
    void evaluatePrintsAggregateStatsVerdictsAndPerQuestionLines() {
        when(service.evaluate()).thenReturn(evaluate());

        String out = run("--day=28", "--evaluate");

        assertThat(out).contains("=== Оценка локальной RAG-системы ===")
                .contains("Вопросов: 10 | повторов на вопрос: 2")
                .contains("Поиск: ожидаемый источник найден в 1 из 10 вопросов (10.0%)")
                .contains("Локальная модель: ответов 20 | покрытие 6.7% | "
                        + "отклонение ±0.0 п.п. | задержка 3100 мс | 5.3 ток/с")
                .contains("Облачная модель: ответов 20 | покрытие 6.7% | "
                        + "отклонение ±1.4 п.п. | задержка 1000 мс | 25.0 ток/с")
                .contains("Качество: Качество (покрытие ключевых слов): "
                        + "локальная модель 6.7% против облачной 6.7%.")
                .contains("Скорость: Средняя задержка")
                .contains("Стабильность: Отклонение покрытия между повторами")
                .contains("--- По вопросам ---")
                .contains("  q01 | ретрив: да | лок: 100.0% | обл: 100.0%");
    }

    @Test
    void evaluatePrintsUnavailableCloudEngineAsReason() {
        when(service.evaluate()).thenReturn(new Day28EvalResponse(10, 2, 1, 10.0,
                new Day28EngineStats(true, "", 20, 0, 6.7, 0.0, 3_100.0, 120.0, 5.3),
                new Day28EngineStats(false, "облачный ключ не задан (LLM_API_KEY)",
                        0, 0, null, null, null, null, null),
                "Локальная модель 6.7%.",
                "Средняя задержка: локальная 3100 мс.",
                "Повторов на вопрос: 2 — стабильность локальной модели измерялась.",
                List.of(new Day28EvalItem("q01", "Вопрос", List.of("ключевое"),
                        List.of("embeddings.md"), List.of(), false,
                        List.of(0.0, 0.0), List.of(10L, 10L),
                        List.of(), List.of()))));

        String out = run("--day=28", "--evaluate");

        assertThat(out).contains("Облачная модель: недоступна — "
                + "облачный ключ не задан (LLM_API_KEY)")
                .contains("  q01 | ретрив: НЕТ | лок: 0.0% | обл: —");
    }

    @Test
    void noOptionsPrintsHelp() {
        String out = run("--day=28");

        assertThat(out).contains("Подсказка по веб-интерфейсу: "
                + "http://localhost:8080/day28.html")
                .contains("--check")
                .contains("--ask=")
                .contains("--compare=")
                .contains("--evaluate");
    }
}
