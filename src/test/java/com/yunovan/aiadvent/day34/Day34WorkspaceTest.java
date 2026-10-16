package com.yunovan.aiadvent.day34;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day34WorkspaceTest {

    private static Day34Properties properties(Path root) {
        return new Day34Properties(0, null, null, null, root.toString(), null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    private static Day34Workspace workspace(Path root) {
        return new Day34Workspace(properties(root));
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @Test
    void listsOnlySupportedFiles(@TempDir Path root) throws IOException {
        write(root, "src/main/java/demo/Foo.java", "class Foo {}\n");
        write(root, "notes.txt", "hello\n");
        write(root, "binary.exe", "nope\n");

        List<String> paths = workspace(root).list(null, null).stream()
                .map(Day34FileInfo::path).toList();

        assertThat(paths).contains("src/main/java/demo/Foo.java", "notes.txt");
        assertThat(paths).noneMatch(path -> path.endsWith(".exe"));
    }

    @Test
    void contentReadsAndTruncates(@TempDir Path root) throws IOException {
        write(root, "a.md", "line1\nline2\nline3\nline4\nline5\n");

        Day34FileContent content = workspace(root).content("a.md", 1, 2);

        assertThat(content.content()).isEqualTo("line1\nline2");
        assertThat(content.startLine()).isEqualTo(1);
        assertThat(content.endLine()).isEqualTo(2);
        assertThat(content.totalLines()).isEqualTo(5);
        assertThat(content.truncated()).isTrue();
    }

    @Test
    void grepReportsLineNumbers(@TempDir Path root) throws IOException {
        write(root, "src/Foo.java", "nothing\nWidget w = new Widget();\nplain\n");

        List<Day34SearchMatch> matches = workspace(root).grep("Widget", null, null);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().path()).isEqualTo("src/Foo.java");
        assertThat(matches.getFirst().line()).isEqualTo(2);
        assertThat(matches.getFirst().text()).contains("Widget");
    }

    @Test
    void writeCreatesThenUpdates(@TempDir Path root) {
        Day34Workspace ws = workspace(root);

        Day34WriteResult created = ws.write("out/report.md", "# Report\n");
        Day34WriteResult updated = ws.write("out/report.md", "# Report v2\n");

        assertThat(created.action()).isEqualTo("create");
        assertThat(updated.action()).isEqualTo("update");
        assertThat(updated.bytes()).isPositive();
        assertThat(ws.exists("out/report.md")).isTrue();
        assertThat(ws.readText("out/report.md")).isEqualTo("# Report v2\n");
    }

    @Test
    void patchReplacesFirstOccurrence(@TempDir Path root) throws IOException {
        write(root, "a.md", "alpha\nbeta\nalpha\n");

        String updated = workspace(root).patch("a.md", "alpha", "ALPHA");

        assertThat(updated).isEqualTo("ALPHA\nbeta\nalpha\n");
    }

    @Test
    void statReturnsLineAndByteCounts(@TempDir Path root) throws IOException {
        write(root, "a.md", "one\ntwo\nthree\n");

        Day34FileInfo info = workspace(root).stat("a.md");

        assertThat(info.path()).isEqualTo("a.md");
        assertThat(info.lines()).isEqualTo(3);
        assertThat(info.sizeBytes()).isPositive();
    }

    @Test
    void rejectsPathTraversal(@TempDir Path root) {
        assertThatThrownBy(() -> workspace(root).content("../secret.md", null, null))
                .isInstanceOf(Day34Exception.class)
                .hasMessageContaining("вне рабочего каталога");
    }

    @Test
    void rejectsDisallowedExtension(@TempDir Path root) {
        assertThatThrownBy(() -> workspace(root).write("payload.exe", "boom"))
                .isInstanceOf(Day34Exception.class)
                .hasMessageContaining("расширение");
        assertThatThrownBy(() -> workspace(root).content("payload.exe", null, null))
                .isInstanceOf(Day34Exception.class)
                .hasMessageContaining("расширение");
    }
}
