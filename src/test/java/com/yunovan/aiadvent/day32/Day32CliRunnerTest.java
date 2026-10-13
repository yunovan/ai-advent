package com.yunovan.aiadvent.day32;

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

class Day32CliRunnerTest {

    private final Day32ReviewService service = mock(Day32ReviewService.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day32CliRunner runner = new Day32CliRunner(service, context);

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

    private static Day32HealthResponse health() {
        return new Day32HealthResponse("ok", "/repo", "main", "main", 6, 240, 900, 120_000,
                new Day32ModelStatus("http://localhost:11434", "qwen2.5:3b", true, "0.35.1", true, ""));
    }

    private static Day32ReviewResponse review() {
        return new Day32ReviewResponse("main", "HEAD", "PR", 1,
                List.of(new Day32ChangedFile("src/main/java/Foo.java", "modified", 3, 1)),
                120, 3, 1, false,
                List.of(Day32Finding.of("high", "bug", "src/main/java/Foo.java", 12, "риск NPE")),
                List.of(), List.of(), "Ревью моделью qwen2.5:3b: файлов 1, замечаний 1.",
                "## Потенциальные баги\n- src/main/java/Foo.java:12 — риск NPE\n\n"
                        + "## Архитектурные проблемы\n- нет\n\n## Рекомендации\n- нет",
                false, "qwen2.5:3b", 2, List.of("architecture.md"), List.of("architecture.md"),
                700L, 150, 60, 8.0, "");
    }

    @Test
    void ignoresOtherDays() {
        String output = run("--day=31");

        assertThat(output).isEmpty();
        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsIndexAndModel() {
        when(service.health()).thenReturn(health());

        String output = run("--day=32", "--check");

        assertThat(output).contains("Автоматическое ревью кода");
        assertThat(output).contains("ветка main");
        assertThat(output).contains("из них кода 240");
        assertThat(output).contains("qwen2.5:3b");
    }

    @Test
    void reviewPrintsSections() {
        when(service.review(any())).thenReturn(review());

        String output = run("--day=32", "--review", "--title=PR");

        assertThat(output).contains("Ревью pull request");
        assertThat(output).contains("риск NPE");
        assertThat(output).contains("## Рекомендации");
    }

    @Test
    void reviewWritesMarkdownToOutFile(@TempDir Path temp) {
        when(service.review(any())).thenReturn(review());
        Path out = temp.resolve("ai-review.md");

        run("--day=32", "--review", "--out=" + out);

        assertThat(Files.exists(out)).isTrue();
        assertThat(read(out)).contains("риск NPE");
    }

    @Test
    void usageWhenNoAction() {
        String output = run("--day=32");

        assertThat(output).contains("--check");
        assertThat(output).contains("--review");
        verifyNoInteractions(service);
    }

    @Test
    void printsErrorWhenGitUnavailable() {
        when(service.review(any())).thenThrow(new Day32GitException("git недоступен"));

        String output = run("--day=32", "--review");

        assertThat(output).contains("ОШИБКА git:");
        assertThat(output).contains("git недоступен");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
