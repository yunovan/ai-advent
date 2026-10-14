package com.yunovan.aiadvent.day33;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day33McpRoundTripTest {

    private Day33McpServer server;
    private Day33McpClient client;

    @BeforeEach
    void setUp() throws IOException {
        int port = freePort();
        Day33Properties properties = new Day33Properties(port, null, null, null, "data/day33-none",
                "build/day33-none", null, null, null, null, null, null);
        Day33CrmStore crm = new Day33CrmStore(properties);
        server = new Day33McpServer(properties, crm);
        server.start();
        client = new Day33McpClient(properties);
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
    void listsCrmTools() {
        Day33Connection connection = client.connect();

        assertThat(connection.sessionId()).isNotBlank();
        assertThat(client.listTools(connection))
                .extracting(Day33ToolInfo::name)
                .contains("crm_user", "crm_ticket", "crm_user_tickets",
                        "crm_search_tickets", "crm_open_tickets");
    }

    @Test
    void callsTicketTool() {
        Day33Connection connection = client.connect();

        Day33ToolResult result = client.callTool(connection, "crm_ticket", Map.of("ticketId", "T-1001"));

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).contains("Не работает авторизация").contains("U-1001");
    }

    @Test
    void callsUserTool() {
        Day33Connection connection = client.connect();

        Day33ToolResult result = client.callTool(connection, "crm_user", Map.of("userId", "U-1001"));

        assertThat(result.content()).contains("Иван Петров").contains("Business");
    }

    @Test
    void missingRequiredArgumentReturnsError() {
        Day33Connection connection = client.connect();

        assertThatThrownBy(() -> client.callTool(connection, "crm_ticket", Map.of()))
                .isInstanceOf(Day33McpException.class);
    }

    @Test
    void unknownToolReturnsError() {
        Day33Connection connection = client.connect();

        assertThatThrownBy(() -> client.callTool(connection, "crm_nope", Map.of()))
                .isInstanceOf(Day33McpException.class);
    }
}
