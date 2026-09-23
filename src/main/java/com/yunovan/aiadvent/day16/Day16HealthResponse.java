package com.yunovan.aiadvent.day16;

import java.util.List;

public record Day16HealthResponse(
        boolean connected,
        String protocolVersion,
        String serverName,
        String serverVersion,
        String sessionId,
        int toolCount) {

    public static Day16HealthResponse disconnected(String protocolVersion, String serverName, String serverVersion) {
        return new Day16HealthResponse(false, protocolVersion, serverName, serverVersion, null, 0);
    }

    public static Day16HealthResponse connected(Day16Connection connection, List<Day16ToolInfo> tools) {
        return new Day16HealthResponse(true, connection.protocolVersion(), connection.serverName(),
                connection.serverVersion(), connection.sessionId(), tools.size());
    }
}