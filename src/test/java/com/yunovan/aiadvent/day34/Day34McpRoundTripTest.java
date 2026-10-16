package com.yunovan.aiadvent.day34;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day34McpRoundTripTest {

    private Day34McpServer server;
    private Day34McpClient client;
    private Day34Properties properties;

    @TempDir
    Path root;

    @BeforeEach
    void setUp() throws IOException {
        int port = freePort();
        properties = new Day34Properties(port, null, null, null, root.toString(), null, null, null,
                null, null, null, null, null, null, null, null, null, null, null);
        Day34Workspace workspace = new Day34Workspace(properties);
        server = new Day34McpServer(properties, workspace);
        server.start();
        client = new Day34McpClient(properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void listsFileTools() {
        Day34Connection connection = client.connect();

        assertThat(connection.sessionId()).isNotBlank();
        assertThat(client.listTools(connection)).extracting(Day34ToolInfo::name)
                .containsExactlyInAnyOrder("fs_list", "fs_read", "fs_grep", "fs_write", "fs_patch", "fs_stat");
    }

    @Test
    void writesReadsAndStats() {
        Day34Connection connection = client.connect();

        Day34WriteResult written = client.writeFile(connection, "docs/note.md", "# Note");
        String content = client.readFile(connection, "docs/note.md", null, null);
        Day34FileInfo info = client.stat(connection, "docs/note.md");

        assertThat(written.action()).isEqualTo("create");
        assertThat(content).isEqualTo("# Note");
        assertThat(info.lines()).isEqualTo(1);
        assertThat(info.sizeBytes()).isPositive();
    }

    @Test
    void listsWrittenFiles() {
        Day34Connection connection = client.connect();
        client.writeFile(connection, "docs/note.md", "# Note");

        assertThat(client.listFiles(connection, "docs", null))
                .extracting(Day34FileInfo::path)
                .containsExactly("docs/note.md");
    }

    @Test
    void searchesAndPatches() {
        Day34Connection connection = client.connect();
        client.writeFile(connection, "src/Foo.java", "class Foo { Widget w; }");

        assertThat(client.search(connection, "Widget", "*.java", null))
                .singleElement()
                .satisfies(match -> {
                    assertThat(match.path()).isEqualTo("src/Foo.java");
                    assertThat(match.line()).isEqualTo(1);
                });

        client.patchFile(connection, "src/Foo.java", "Widget", "Gadget");
        assertThat(client.readFile(connection, "src/Foo.java", null, null)).contains("Gadget");
    }

    @Test
    void unknownToolReturnsError() {
        Day34Connection connection = client.connect();

        assertThatThrownBy(() -> client.callTool(connection, "fs_nope", Map.of()))
                .isInstanceOf(Day34McpException.class);
    }

    @Test
    void sandboxRejectsTraversal() {
        Day34Connection connection = client.connect();

        assertThatThrownBy(() -> client.readFile(connection, "../escape.md", null, null))
                .isInstanceOf(Day34McpException.class)
                .hasMessageContaining("вне рабочего каталога");
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
