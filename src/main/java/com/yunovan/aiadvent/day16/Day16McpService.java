package com.yunovan.aiadvent.day16;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class Day16McpService {

    private final Day16McpClient client;
    private Day16Connection connection;

    public Day16McpService(Day16McpClient client) {
        this.client = client;
    }

    public Day16HealthResponse health() {
        try {
            Day16Connection current = connection();
            List<Day16ToolInfo> tools = client.listTools(current);
            return Day16HealthResponse.connected(current, tools);
        } catch (Day16McpException ex) {
            connection = null;
            throw ex;
        }
    }

    public List<Day16ToolInfo> tools() {
        try {
            return client.listTools(connection());
        } catch (Day16McpException ex) {
            connection = null;
            throw ex;
        }
    }

    public Day16CallResponse call(String tool, Map<String, Object> arguments) {
        try {
            Day16ToolResult result = client.callTool(connection(), tool, arguments);
            return new Day16CallResponse(result.tool(), result.content(), result.isError());
        } catch (Day16McpException ex) {
            connection = null;
            throw ex;
        }
    }

    private Day16Connection connection() {
        if (connection == null) {
            connection = client.connect();
        }
        return connection;
    }
}