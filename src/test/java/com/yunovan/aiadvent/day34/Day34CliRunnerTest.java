package com.yunovan.aiadvent.day34;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day34CliRunnerTest {

    private final Day34FileService service = mock(Day34FileService.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day34CliRunner runner = new Day34CliRunner(service, context);

    private String run(String... args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            runner.run(new DefaultApplicationArguments(args));
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static Day34HealthResponse health() {
        return new Day34HealthResponse("ok", "C:/project", true, 120, 8, 400, 200_000,
                new Day34McpStatus(true, "http://localhost:9097/mcp", 6, ""),
                new Day34ModelStatus("http://localhost:11434", "qwen2.5:3b", true, "0.35.1", true, ""));
    }

    private static Day34RunResponse response() {
        return new Day34RunResponse("usages", "Поиск использований", "Найди Widget", "Widget",
                List.of(Day34PlanStep.of(1, "fs_grep", java.util.Map.of("pattern", "Widget"),
                        "найти использования")),
                List.of(new Day34StepResult(1, "fs_grep", "{pattern=Widget}", "совпадений: 2", true)),
                List.of("Соединение: mcp"), 2, List.of("A.java", "B.java"), 2, List.of(),
                List.of(new Day34Artifact("build/out/usages-widget.md", "create", "# Использования", 16,
                        "--- /dev/null\n+++ b/build/out/usages-widget.md\n+# Использования\n")),
                "--- /dev/null\n+++ b/build/out/usages-widget.md\n+# Использования\n",
                List.of(), "Найдено 2 использований", "# Использования\n\nНайдено 2 совпадений.",
                false, "qwen2.5:3b", 850L, 120, 60, 11.0, "Файловые операции: mcp");
    }

    @Test
    void ignoresOtherDays() {
        String output = run("--day=33");

        assertThat(output).isEmpty();
        verifyNoInteractions(service);
    }

    @Test
    void printsUsageWhenNoGoal() {
        String output = run("--day=34");

        assertThat(output).contains("Ассистент по файлам проекта");
        assertThat(output).contains("--run=");
    }

    @Test
    void checkPrintsWorkspaceAndMcp() {
        when(service.health()).thenReturn(health());

        String output = run("--day=34", "--check");

        assertThat(output).contains("Состояние ассистента файлов");
        assertThat(output).contains("Исходников: 120");
        assertThat(output).contains("6 инструментов");
        assertThat(output).contains("0.35.1");
    }

    @Test
    void filesPrintsEntries() {
        when(service.files(any(), any())).thenReturn(List.of(
                new Day34FileInfo("src/Foo.java", 120, 10, "")));

        String output = run("--day=34", "--files", "--prefix=src");

        assertThat(output).contains("Файлы проекта: 1");
        assertThat(output).contains("src/Foo.java");
    }

    @Test
    void runPrintsSummaryAndArtifacts() {
        when(service.run(any())).thenReturn(response());

        String output = run("--day=34", "--run=Найди Widget");

        assertThat(output).contains("Задача: Поиск использований");
        assertThat(output).contains("Найдено 2 использований");
        assertThat(output).contains("fs_grep");
        assertThat(output).contains("build/out/usages-widget.md");
    }

    @Test
    void runWritesDocumentToOutFile(@TempDir Path temp) throws Exception {
        when(service.run(any())).thenReturn(response());
        Path out = temp.resolve("report.md");

        run("--day=34", "--run=Найди Widget", "--out=" + out);

        assertThat(Files.readString(out)).isEqualTo(response().markdown());
    }

    @Test
    void reportsErrorsGracefully() {
        when(service.run(any())).thenThrow(new Day34McpException("MCP недоступен"));

        String output = run("--day=34", "--run=Найди Widget");

        assertThat(output).contains("Ошибка MCP");
    }
}
