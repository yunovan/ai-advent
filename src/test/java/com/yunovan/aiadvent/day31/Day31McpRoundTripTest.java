package com.yunovan.aiadvent.day31;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day31McpRoundTripTest {

    private Day31McpServer server;
    private Day31McpClient client;

    @BeforeEach
    void setUp() throws IOException {
        int port = freePort();
        Day31Properties properties = new Day31Properties(port, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
        server = new Day31McpServer(properties, new Day31GitTool(properties));
        server.start();
        client = new Day31McpClient(properties);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void listsGitTools() {
        Day31Connection connection = client.connect();

        assertThat(connection.sessionId()).isNotBlank();
        assertThat(client.listTools(connection))
                .extracting(Day31ToolInfo::name)
                .contains("git_branch", "git_status", "git_files", "git_diff", "git_log");
    }

    @Test
    void callsGitBranchTool() {
        Day31Connection connection = client.connect();

        Day31ToolResult result = client.callTool(connection, "git_branch", Map.of());

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Ветка:");
    }

    @Test
    void unknownToolReturnsError() {
        Day31Connection connection = client.connect();

        assertThatThrownBy(() -> client.callTool(connection, "git_nope", Map.of()))
                .isInstanceOf(Day31McpException.class);
    }
}
