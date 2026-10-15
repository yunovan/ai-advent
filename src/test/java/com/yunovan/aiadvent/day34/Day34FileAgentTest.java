package com.yunovan.aiadvent.day34;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day26.Day26Answer;
import com.yunovan.aiadvent.day26.Day26ChatOptions;
import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day34FileAgentTest {

    private static final String MODEL_REPLY = "## Сгенерировано\n\nДокумент по коду.";

    @TempDir
    Path root;

    private Day26LocalLlmClient llm;
    private Day34FileAgent agent;

    @BeforeEach
    void setUp() throws IOException {
        write("src/main/java/demo/Foo.java", """
                package demo;
                class Foo {
                    Widget widget = new Widget();
                    void run() {
                        System.out.println("debug");
                        // TODO: доработать
                    }
                }
                """);
        write("src/main/java/demo/Bar.java", "package demo;\nclass Bar { Widget w; }\n");
        write("README.md", "# Demo project\n");
        write("docs/guide.md", "# Guide\n");

        Day34Properties properties = new Day34Properties(closedPort(), null, null, null,
                root.toString(), null, null, null, "build/out", null, null, null, null, null, null,
                null, null, null, null);
        llm = mock(Day26LocalLlmClient.class);
        when(llm.chat(anyList(), any(Day26ChatOptions.class))).thenReturn(answer(MODEL_REPLY));
        agent = new Day34FileAgent(properties, new Day34Workspace(properties),
                new Day34McpClient(properties), new Day34GoalPlanner(), llm);
    }

    private Day26Answer answer(String reply) {
        return new Day26Answer("prompt", reply, "qwen2.5:3b", "http://localhost:11434",
                120L, 40, 25, 12.5);
    }

    @Test
    void findsUsagesAcrossFiles() {
        Day34RunResponse response = agent.run(new Day34RunRequest(
                "Найди использования \"Widget\"", null, null, null, null));

        assertThat(response.task()).isEqualTo("usages");
        assertThat(response.matches()).isEqualTo(2);
        assertThat(response.filesTouched()).isGreaterThanOrEqualTo(2);
        assertThat(response.markdown()).contains("Widget").contains("Использования");
        assertThat(response.fallback()).isFalse();
        assertThat(response.model()).isEqualTo("qwen2.5:3b");
        assertThat(response.plan()).isNotEmpty();
        assertThat(response.note()).contains("local");
    }

    @Test
    void checksInvariantsAndReportsFindings() {
        Day34RunResponse response = agent.run(new Day34RunRequest(
                "Проверь инварианты кода", "invariants", null, null, null));

        assertThat(response.task()).isEqualTo("invariants");
        assertThat(response.findings()).isNotEmpty();
        assertThat(response.findings()).extracting(Day34Finding::rule)
                .contains("no-system-out");
        assertThat(response.markdown()).contains("Отчёт о проверке инвариантов");
    }

    @Test
    void generatesDocumentFromModel() {
        Day34RunResponse response = agent.run(new Day34RunRequest(
                "Сгенерируй README для проекта", null, null, null, null));

        assertThat(response.task()).isEqualTo("readme");
        assertThat(response.artifacts()).hasSize(1);
        Day34Artifact artifact = response.artifacts().getFirst();
        assertThat(artifact.content()).isEqualTo(MODEL_REPLY);
        assertThat(artifact.changed()).isTrue();
        assertThat(response.written()).isEmpty();
        assertThat(response.markdown()).isEqualTo(MODEL_REPLY);
    }

    @Test
    void writeModePersistsArtifact() {
        Day34RunResponse response = agent.run(new Day34RunRequest(
                "Сгенерируй README для проекта", "readme", null, true, null));

        String path = response.artifacts().getFirst().path();
        assertThat(response.written()).contains(path);
        assertThat(Files.exists(root.resolve(path))).isTrue();
        assertThat(response.note()).contains("записи");
    }

    @Test
    void fallsBackWhenModelUnavailable() {
        when(llm.chat(anyList(), any(Day26ChatOptions.class)))
                .thenThrow(new Day26LlmException("модель недоступна"));

        Day34RunResponse response = agent.run(new Day34RunRequest(
                "Сгенерируй README для проекта", "readme", null, null, null));

        assertThat(response.fallback()).isTrue();
        assertThat(response.markdown()).contains("без языковой модели");
        assertThat(response.artifacts()).hasSize(1);
    }

    @Test
    void updateDocRequiresExistingTarget() {
        assertThatThrownBy(() -> agent.run(new Day34RunRequest(
                "Обнови документацию по API", "update", "docs/missing.md", null, null)))
                .isInstanceOf(Day34Exception.class)
                .hasMessageContaining("не найден");
    }

    @Test
    void runWithoutGoalOrTaskFails() {
        assertThatThrownBy(() -> agent.run(new Day34RunRequest("  ", null, null, null, null)))
                .isInstanceOf(Day34Exception.class)
                .hasMessageContaining("Не указана цель");
    }

    private void write(String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
