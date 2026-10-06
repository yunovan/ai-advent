package com.yunovan.aiadvent.day26;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day26CliRunnerTest {

    private final Day26Service service = mock(Day26Service.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day26CliRunner runner = new Day26CliRunner(service, context);

    private String run(String... args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.run(new DefaultApplicationArguments(args));
        } finally {
            System.setOut(original);
        }
        return buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Day26HealthResponse running() {
        return new Day26HealthResponse("http://localhost:11434", "qwen2.5:3b", true,
                "0.35.1", true,
                List.of(new Day26InstalledModel("qwen2.5:3b", 1_998_578_976L, "3.09B", "Q4_K_M")),
                "");
    }

    private static Day26RunReport okReport() {
        return new Day26RunReport("http://localhost:11434", "qwen2.5:3b",
                List.of(
                        Day26TaskResult.ok(Day26Task.ALL.get(0), answer(3_100, 45, 11)),
                        Day26TaskResult.ok(Day26Task.ALL.get(1), answer(12_600, 56, 136)),
                        Day26TaskResult.ok(Day26Task.ALL.get(2), answer(15_200, 71, 164))),
                3, 0, 30_900, "все 3 запросов выполнены: локальная LLM запущена и отвечает");
    }

    private static Day26Answer answer(long latencyMs, int promptTokens, int outputTokens) {
        return new Day26Answer("вопрос", "Я Qwen, языковая модель от Alibaba Cloud.",
                "qwen2.5:3b", "http://localhost:11434", latencyMs, promptTokens, outputTokens, 5.3);
    }

    @Test
    void otherDayTriggersNoInteractions() {
        runner.run(new DefaultApplicationArguments("--day=25", "--check"));

        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsRunningServerAndInstalledModel() {
        when(service.health()).thenReturn(running());

        String out = run("--day=26", "--check");

        assertThat(out).contains("=== ЛОКАЛЬНАЯ LLM ===")
                .contains("endpoint: http://localhost:11434")
                .contains("модель: qwen2.5:3b")
                .contains("сервер запущен: ДА (версия 0.35.1)")
                .contains("модель установлена: ДА")
                .contains("qwen2.5:3b | 1905 МБ | 3.09B | Q4_K_M");
    }

    @Test
    void checkPrintsStoppedServerWithoutCrashing() {
        when(service.health()).thenReturn(new Day26HealthResponse("http://localhost:11434",
                "qwen2.5:3b", false, "", false, List.of(),
                "Локальный LLM недоступен на http://localhost:11434"));

        String out = run("--day=26", "--check");

        assertThat(out).contains("сервер запущен: НЕТ")
                .contains("причина: Локальный LLM недоступен")
                .doesNotContain("Exception");
    }

    @Test
    void tasksPrintAllThreePrompts() {
        when(service.tasks()).thenReturn(Day26Task.ALL);

        String out = run("--day=26", "--tasks");

        assertThat(out).contains("ЗАПРОСЫ РАЗНОЙ СЛОЖНОСТИ (3)")
                .contains("[simple] Простой запрос (simple)")
                .contains("[medium] Объяснение двух мыслей (medium)")
                .contains("[complex] Код и рассуждение (complex)")
                .contains("Привет! Ответь одной фразой: кто ты такой?")
                .contains("Чем отличается CPU от GPU при запуске языковых моделей?")
                .contains("убирает дубликаты");
    }

    @Test
    void runPrintsTimingsTokensAndVerdict() {
        when(service.run()).thenReturn(okReport());

        String out = run("--day=26", "--run");

        assertThat(out).contains("=== ТРИ ЗАПРОСА К ЛОКАЛЬНОЙ LLM ===")
                .contains("[simple] Простой запрос (simple) — ОК")
                .contains("[medium] Объяснение двух мыслей (medium) — ОК")
                .contains("[complex] Код и рассуждение (complex) — ОК")
                .contains("задержка: 3100 мс | токены: 45+ 11")
                .contains("задержка: 15200 мс | токены: 71+ 164")
                .contains("итого: 3/3 | 30900 мс")
                .contains("вердикт: все 3 запросов выполнены: локальная LLM запущена и отвечает");
    }

    @Test
    void runPrintsFailureForStoppedServer() {
        when(service.run()).thenReturn(new Day26RunReport("http://localhost:11434", "qwen2.5:3b",
                Day26Task.ALL.stream()
                        .map(task -> Day26TaskResult.failed(task, "Локальный LLM недоступен"))
                        .toList(),
                0, 3, 5, "ни один из 3 запросов не выполнен: запустите локальный LLM "
                        + "(ollama serve) и проверьте endpoint"));

        String out = run("--day=26", "--run");

        assertThat(out).contains("— ОШИБКА")
                .contains("ошибка: Локальный LLM недоступен")
                .contains("итого: 0/3 | 5 мс");
    }

    @Test
    void askPrintsAnswerWithMetrics() {
        when(service.ask("Сколько будет 17*23?")).thenReturn(answer(2_500, 12, 5));

        String out = run("--day=26", "--ask=Сколько будет 17*23?");

        assertThat(out).contains("модель: qwen2.5:3b (http://localhost:11434)")
                .contains("ответ: Я Qwen, языковая модель от Alibaba Cloud.")
                .contains("задержка: 2500 мс | токены: 12+ 5 | скорость: 5.3 ток/с");
    }

    @Test
    void blankAskPrintsErrorInsteadOfStackTrace() {
        when(service.ask(any())).thenThrow(new IllegalArgumentException("Запрос не может быть пустым"));

        String out = run("--day=26", "--ask=");

        assertThat(out).contains("ОШИБКА: Запрос не может быть пустым")
                .doesNotContain("Exception");
    }

    @Test
    void helpPrintsUsageWithoutCallingService() {
        String out = run("--day=26");

        assertThat(out).contains("Состояние: --check")
                .contains("Три запроса: --run")
                .contains("--ask=");
        verifyNoInteractions(service);
    }
}
