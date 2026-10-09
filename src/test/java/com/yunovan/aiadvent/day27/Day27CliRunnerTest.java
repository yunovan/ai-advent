package com.yunovan.aiadvent.day27;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day26.Day26LlmException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day27CliRunnerTest {

    private final Day27ChatService service = mock(Day27ChatService.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day27CliRunner runner = new Day27CliRunner(service, context);

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

    private static Day27HealthResponse running() {
        return new Day27HealthResponse("http://localhost:11434", "qwen2.5:3b", true,
                "0.35.1", true, false, 40, 50, 3, "");
    }

    private static Day27ChatTurn turn() {
        return new Day27ChatTurn("cli", 1, "Кто ты?", "Я локальный ассистент.", "qwen2.5:3b",
                "http://localhost:11434", 3_100, 45, 25, 5.3, List.of(), 2);
    }

    @Test
    void otherDayTriggersNoInteractions() {
        runner.run(new DefaultApplicationArguments("--day=26", "--check"));

        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsLocalConnectionAndNoCloud() {
        when(service.health()).thenReturn(running());

        String out = run("--day=27", "--check");

        assertThat(out).contains("=== ЛОКАЛЬНЫЙ АССИСТЕНТ ===")
                .contains("endpoint: http://localhost:11434")
                .contains("модель: qwen2.5:3b")
                .contains("сервер запущен: ДА (версия 0.35.1)")
                .contains("модель установлена: ДА")
                .contains("облачные модели: НЕТ (только локальный Ollama)")
                .contains("история: до 40 сообщений | сессий: 3 (максимум 50)");
    }

    @Test
    void checkPrintsStoppedServerWithoutCrashing() {
        when(service.health()).thenReturn(new Day27HealthResponse("http://localhost:11434",
                "qwen2.5:3b", false, "", false, false, 40, 50, 0,
                "Локальный LLM недоступен на http://localhost:11434 (connection refused)"));

        String out = run("--day=27", "--check");

        assertThat(out).contains("сервер запущен: НЕТ")
                .contains("причина: Локальный LLM недоступен")
                .contains("облачные модели: НЕТ");
    }

    @Test
    void askPrintsDialogTurnWithMetrics() {
        when(service.chat("cli", "Кто ты?")).thenReturn(turn());

        String out = run("--day=27", "--ask=Кто ты?");

        assertThat(out).contains("=== ДИАЛОГ · сессия cli, ход 1 ===")
                .contains("вы: Кто ты?")
                .contains("ассистент: Я локальный ассистент.")
                .contains("задержка: 3100 мс")
                .contains("токены: 45 вход / 25 выход")
                .contains("скорость: 5.3 ток/с")
                .contains("модель: qwen2.5:3b");
    }

    @Test
    void historyPrintsStoredDialog() {
        when(service.history("cli")).thenReturn(List.of(
                new Day27Message("user", "Привет", 1, 0, 0, 0, 0.0),
                new Day27Message("assistant", "Здравствуйте!", 1, 2_500, 40, 20, 8.0)));

        String out = run("--day=27", "--history");

        assertThat(out).contains("=== ИСТОРИЯ ДИАЛОГА (2 сообщений) ===")
                .contains("[1] пользователь: Привет")
                .contains("[1] ассистент: Здравствуйте! (2500 мс, 40+20 токенов)");
    }

    @Test
    void historyPrintsEmptyNotice() {
        when(service.history("cli")).thenReturn(List.of());

        String out = run("--day=27", "--history");

        assertThat(out).contains("История диалога пуста.");
    }

    @Test
    void resetClearsCliSession() {
        String out = run("--day=27", "--reset");

        assertThat(out).contains("История сессии cli очищена.");
        verify(service).reset("cli");
    }

    @Test
    void llmFailurePrintsErrorLine() {
        when(service.chat("cli", "Привет"))
                .thenThrow(new Day26LlmException("Локальный LLM недоступен на http://localhost:11434"));

        String out = run("--day=27", "--ask=Привет");

        assertThat(out).contains("ОШИБКА: Локальный LLM недоступен");
    }

    @Test
    void blankAskPrintsValidationErrors() {
        when(service.chat(org.mockito.ArgumentMatchers.eq("cli"),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("Сообщение не может быть пустым"));

        String out = run("--day=27", "--ask=");

        assertThat(out).contains("ОШИБКА: Сообщение не может быть пустым");
    }

    @Test
    void noOptionsPrintsHelp() {
        String out = run("--day=27");

        assertThat(out).contains("Локальный ассистент: http://localhost:8080/day27.html")
                .contains("--check")
                .contains("--ask=")
                .contains("--history")
                .contains("--reset");
    }
}
