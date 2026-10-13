package com.yunovan.aiadvent.day31;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day31CliRunnerTest {

    private final Day31DeveloperAssistantService service = mock(Day31DeveloperAssistantService.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day31CliRunner runner = new Day31CliRunner(service, context);

    private String run(String... args) {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        java.io.PrintStream original = System.out;
        System.setOut(new java.io.PrintStream(buffer, true, java.nio.charset.StandardCharsets.UTF_8));
        try {
            runner.run(new DefaultApplicationArguments(args));
        } finally {
            System.setOut(original);
        }
        return buffer.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Day31HealthResponse health() {
        return new Day31HealthResponse("ok", "http://localhost:9095/mcp", "/repo", "main", true,
                List.of("README.md", "architecture.md"), 42, 12_000,
                new Day31McpStatus(true, "http://localhost:9095/mcp", 5, ""),
                new Day31ModelStatus("http://localhost:11434", "qwen2.5:3b", true, "0.35.1", true, ""));
    }

    private static Day31HelpResponse help() {
        Day21Chunk chunk = new Day21Chunk("fixed", "docs/architecture.md", "architecture.md",
                "Архитектура", "Слои RAG", "docs/architecture.md#fixed#0001", 0, 60, "текст");
        return new Day31HelpResponse("Как устроен RAG?", "RAG описан в docs/architecture.md", false,
                "qwen2.5:3b", "main", false, "Ветка: main", true, 5, 1,
                List.of(new Day21SearchHit(chunk, 0.9, "snippet")), List.of("architecture.md"),
                82.0, 900L, 120, 40, 9.5, "");
    }

    @Test
    void ignoresOtherDays() {
        String output = run("--day=30");

        assertThat(output).isEmpty();
        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsRepositoryDocsAndMcp() {
        when(service.health()).thenReturn(health());

        String output = run("--day=31", "--check");

        assertThat(output).contains("Ассистент разработчика");
        assertThat(output).contains("ветка main");
        assertThat(output).contains("architecture.md");
        assertThat(output).contains("инструментов 5");
    }

    @Test
    void toolsPrintsMcpCatalog() {
        when(service.tools()).thenReturn(new Day31ToolsResponse(true, "http://localhost:9095/mcp",
                1, List.of(new Day31ToolInfo("git_branch", "возвращает ветку")), ""));

        String output = run("--day=31", "--tools");

        assertThat(output).contains("MCP-инструменты проекта");
        assertThat(output).contains("git_branch");
    }

    @Test
    void gitPrintsToolOutput() {
        when(service.git(any(), any())).thenReturn(new Day31GitResponse("git_branch", true, "Ветка: main"));

        String output = run("--day=31", "--git=branch");

        assertThat(output).contains("Инструмент MCP: git_branch");
        assertThat(output).contains("Ветка: main");
    }

    @Test
    void helpPrintsAnswerAndMetrics() {
        when(service.help(any())).thenReturn(help());

        String output = run("--day=31", "--help=Как устроен RAG?");

        assertThat(output).contains("Ответ ассистента разработчика");
        assertThat(output).contains("RAG описан в docs/architecture.md");
        assertThat(output).contains("900 мс");
        assertThat(output).contains("опора на источник 82%");
    }

    @Test
    void askAliasWorks() {
        when(service.help(any())).thenReturn(help());

        String output = run("--day=31", "--ask=Как устроен RAG?");

        assertThat(output).contains("RAG описан в docs/architecture.md");
    }

    @Test
    void usageWhenNoAction() {
        String output = run("--day=31");

        assertThat(output).contains("--check");
        assertThat(output).contains("--tools");
        assertThat(output).contains("--help");
        verifyNoInteractions(service);
    }

    @Test
    void printsErrorWhenGitUnavailable() {
        when(service.git(any(), any())).thenThrow(new Day31GitException("git недоступен"));

        String output = run("--day=31", "--git=branch");

        assertThat(output).contains("ОШИБКА:");
        assertThat(output).contains("git недоступен");
    }
}
