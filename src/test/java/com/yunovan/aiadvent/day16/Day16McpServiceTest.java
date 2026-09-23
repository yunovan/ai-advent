package com.yunovan.aiadvent.day16;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day16McpServiceTest {

    private Day16McpServer server;
    private Day16McpService service;

    @BeforeEach
    void setUp() {
        server = new Day16McpServer(new Day16Properties(0, "/mcp", "ai-advent-mcp", "0.1.0"));
        server.start();
        Day16McpClient client = new Day16McpClient(
                new Day16Properties(server.port(), "/mcp", "ai-advent-mcp", "0.1.0"));
        service = new Day16McpService(client);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void healthReportsConnectionDetailsAndToolCount() {
        Day16HealthResponse health = service.health();

        assertThat(health.connected()).isTrue();
        assertThat(health.protocolVersion()).isEqualTo("2024-11-05");
        assertThat(health.serverName()).isEqualTo("ai-advent-mcp");
        assertThat(health.serverVersion()).isEqualTo("0.1.0");
        assertThat(health.sessionId()).isNotBlank();
        assertThat(health.toolCount()).isEqualTo(2);
    }

    @Test
    void toolsReturnsToolListFromMcp() {
        List<Day16ToolInfo> tools = service.tools();

        assertThat(tools).extracting(Day16ToolInfo::name).containsExactly("day16_sum", "day16_upper");
        assertThat(tools.getFirst().description()).isNotBlank();
    }

    @Test
    void callExecutesRegisteredTool() {
        Day16CallResponse response = service.call("day16_sum", Map.of("a", 6, "b", 7));

        assertThat(response.tool()).isEqualTo("day16_sum");
        assertThat(response.result()).isEqualTo("13");
        assertThat(response.isError()).isFalse();
    }

    @Test
    void callUnknownToolMarksError() {
        Day16CallResponse response = service.call("missing_tool", Map.of());

        assertThat(response.isError()).isTrue();
        assertThat(response.result()).contains("missing_tool");
    }

    @Test
    void throwsWhenServerBecomesUnavailableAndResetsConnection() {
        Day16HealthResponse health = service.health();
        assertThat(health.connected()).isTrue();
        server.stop();

        assertThatThrownBy(service::tools).isInstanceOf(Day16McpException.class);
        assertThatThrownBy(service::health).isInstanceOf(Day16McpException.class);
    }
}