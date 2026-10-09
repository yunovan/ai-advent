package com.yunovan.aiadvent.day29;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day29CliRunnerTest {

    private final Day29OptimizationService service = mock(Day29OptimizationService.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day29CliRunner runner = new Day29CliRunner(service, context);

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

    private static Day29HealthResponse health() {
        return new Day29HealthResponse(
                new Day29ModelReport("http://localhost:11434", "qwen2.5:3b", true, true,
                        "0.35.1", "gguf", "3.1B", "Q4_K_M", 3_085_938_688L, 32_768L,
                        1_998_578_976L, 2_047_774_554L, ""),
                new Day29ProfileInfo("baseline", "Базовый (день 28)", 0.2, 300, null,
                        "day28", "параметры дня 28"),
                new Day29ProfileInfo("tuned", "Оптимизированный", 0.1, 240, 2048,
                        "compact-rag", "компактный шаблон"),
                1, 10, "локальный: без сетевых вызовов");
    }

    private static Day29HealthResponse stopped() {
        return new Day29HealthResponse(
                new Day29ModelReport("http://localhost:11434", "qwen2.5:3b", false, false,
                        "", "", "", "", 0, 0, 0, 0,
                        "Локальный LLM недоступен на http://localhost:11434"),
                health().baseline(), health().tuned(), 1, 10, "локальный поиск");
    }

    private static Day29AnswerResponse answer(String profileId, long latencyMs) {
        return new Day29AnswerResponse(profileId, "Профиль " + profileId,
                "Как устроен поиск?", "Ответ по контексту", false, 50.0,
                java.util.List.of("embeddings.md"), "поиск", latencyMs, 300, 120,
                8.5, "");
    }

    private static Day29AskResponse ask() {
        return new Day29AskResponse("Как устроен поиск?",
                java.util.List.of("embeddings.md"), 2200, 900,
                answer("baseline", 30_000L), answer("tuned", 20_000L),
                "Качество одинаковое: опора на контекст 50.0% в обоих профилях.",
                "Скорость: оптимизированный профиль быстрее на 10000 мс.");
    }

    private static Day29RunResponse benchmark() {
        return new Day29RunResponse(10, 1,
                new Day29ProfileStats("baseline", "Базовый", 10, 0, 76.7, 40.0,
                        27_000.0, 9.0, 520.0, 170.0),
                new Day29ProfileStats("tuned", "Оптимизированный", 10, 1, 75.0, 45.0,
                        18_000.0, 9.5, 310.0, 120.0),
                2_047_774_554L,
                "Качество (покрытие ключевых слов): базовый 76.7% против оптимизированного 75.0%.",
                "Скорость: базовый профиль 27000 мс в среднем, оптимизированный 18000 мс.",
                "Ресурсы: входных токенов на запрос — базовый 520.0 против оптимизированного 310.0.");
    }

    @Test
    void otherDayTriggersNoInteractions() {
        runner.run(new DefaultApplicationArguments("--day=28", "--check"));

        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsQuantizationMemoryAndProfiles() {
        when(service.health()).thenReturn(health());

        String out = run("--day=29", "--check");

        assertThat(out).contains("=== Оптимизация локальной LLM (день 29) ===")
                .contains("Модель: http://localhost:11434 / qwen2.5:3b")
                .contains("Сервер: да (версия 0.35.1) | модель: установлена")
                .contains("Квантование: Q4_K_M | параметры: 3.1B (3085938688)")
                .contains("формат: gguf | контекстное окно: 32768")
                .contains("Размер на диске: 1906 МБ | в памяти сейчас: 1953 МБ")
                .contains("Базовый профиль: temperature 0.2, max_tokens 300, шаблон day28")
                .contains("Оптимизированный профиль: temperature 0.1, max_tokens 240, "
                        + "num_ctx 2048, шаблон compact-rag")
                .contains("Вопросов в --run: 10 | повторов на вопрос: 1");
    }

    @Test
    void checkPrintsStoppedServerWithReason() {
        when(service.health()).thenReturn(stopped());

        String out = run("--day=29", "--check");

        assertThat(out).contains("Сервер: нет")
                .contains("Причина: Локальный LLM недоступен на http://localhost:11434");
    }

    @Test
    void askPrintsBothProfilesPromptShrinkAndVerdicts() {
        when(service.ask("Как устроен поиск?")).thenReturn(ask());

        String out = run("--day=29", "--ask=Как устроен поиск?");

        assertThat(out).contains("=== Сравнение профилей ===")
                .contains("Вопрос: Как устроен поиск?")
                .contains("Источники: embeddings.md")
                .contains("Размер промпта: 2200 символов → 900 символов (−1300)")
                .contains("--- Базовый (Профиль baseline) ---")
                .contains("--- Оптимизированный (Профиль tuned) ---")
                .contains("Ответ: Ответ по контексту")
                .contains("Задержка: 30000 мс")
                .contains("опора на контекст 50.0%")
                .contains("Качество: Качество одинаковое")
                .contains("Скорость: Скорость: оптимизированный профиль быстрее");
    }

    @Test
    void runPrintsStatsAndThreeVerdicts() {
        when(service.run()).thenReturn(benchmark());

        String out = run("--day=29", "--run");

        assertThat(out).contains("=== Бенчмарк профилей ===")
                .contains("Вопросов: 10 | повторов на вопрос: 1")
                .contains("Базовый: ответов 10 | покрытие 76.7% | опора 40.0%")
                .contains("Оптимизированный: ответов 10 | покрытие 75.0%")
                .contains("фолбэков: 1")
                .contains("Память модели после прогона: 1953 МБ")
                .contains("Качество: Качество (покрытие ключевых слов)")
                .contains("Скорость: Скорость: базовый профиль")
                .contains("Ресурсы: Ресурсы: входных токенов");
    }

    @Test
    void blankQuestionPrintsError() {
        when(service.ask("   ")).thenThrow(new IllegalArgumentException("Вопрос не может быть пустым"));

        String out = run("--day=29", "--ask=   ");

        assertThat(out).contains("ОШИБКА: Вопрос не может быть пустым");
    }

    @Test
    void llmFailurePrintsError() {
        when(service.ask("вопрос")).thenThrow(
                new com.yunovan.aiadvent.day26.Day26LlmException("Локальный LLM недоступен"));

        String out = run("--day=29", "--ask=вопрос");

        assertThat(out).contains("ОШИБКА: Локальный LLM недоступен");
    }

    @Test
    void withoutActionsPrintsHint() {
        String out = run("--day=29");

        assertThat(out).contains("http://localhost:8080/day29.html")
                .contains("Состояние: --check")
                .contains("полный бенчмарк: --run");
        verifyNoInteractions(service);
    }
}
