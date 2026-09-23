package com.yunovan.aiadvent.day16;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day16McpClientTest {

    private Day16McpServer server;
    private Day16McpClient client;

    @BeforeEach
    void setUp() {
        server = new Day16McpServer(new Day16Properties(0, "/mcp", "ai-advent-mcp", "0.1.0"));
        server.start();
        client = new Day16McpClient(
                new Day16Properties(server.port(), "/mcp", "ai-advent-mcp", "0.1.0"));
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void connectEstablishesSessionWithServerInfo() {
        Day16Connection connection = client.connect();

        assertThat(connection.protocolVersion()).isEqualTo("2024-11-05");
        assertThat(connection.serverName()).isEqualTo("ai-advent-mcp");
        assertThat(connection.serverVersion()).isEqualTo("0.1.0");
        assertThat(connection.sessionId()).isNotBlank();
        assertThat(connection.capabilities().path("tools").isObject()).isTrue();
    }

    @Test
    void listToolsReturnsAvailableTools() {
        Day16Connection connection = client.connect();

        List<Day16ToolInfo> tools = client.listTools(connection);

        assertThat(tools).extracting(Day16ToolInfo::name).containsExactly("day16_sum", "day16_upper");
        assertThat(tools.getFirst().description()).isNotBlank();
    }

    @Test
    void callToolExecutesRegisteredTool() {
        Day16Connection connection = client.connect();

        Day16ToolResult result = client.callTool(connection, "day16_sum", Map.of("a", 6, "b", 7));

        assertThat(result.tool()).isEqualTo("day16_sum");
        assertThat(result.content()).isEqualTo("13");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void callToolUpperCasesText() {
        Day16Connection connection = client.connect();

        Day16ToolResult result = client.callTool(connection, "day16_upper", Map.of("text", "привет"));

        assertThat(result.content()).isEqualTo("ПРИВЕТ");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void callUnknownToolMarksError() {
        Day16Connection connection = client.connect();

        Day16ToolResult result = client.callTool(connection, "missing_tool", Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("missing_tool");
    }

    @Test
    void callToolWithMissingArgumentMarksError() {
        Day16Connection connection = client.connect();

        Day16ToolResult result = client.callTool(connection, "day16_sum", Map.of());

        assertThat(result.isError()).isTrue();
        assertThat(result.content()).contains("Отсутствует аргумент: a");
    }

    @Test
    void connectFailsWhenServerIsDown() {
        int port = server.port();
        server.stop();

        Day16McpClient unreachable = new Day16McpClient(
                new Day16Properties(port, "/mcp", "ai-advent-mcp", "0.1.0"));

        assertThatThrownBy(unreachable::connect)
                .isInstanceOf(Day16McpException.class)
                .hasMessageContaining("MCP недоступен");
    }
}