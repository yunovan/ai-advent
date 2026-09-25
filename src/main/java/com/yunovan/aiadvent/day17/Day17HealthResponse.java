package com.yunovan.aiadvent.day17;

public record Day17HealthResponse(
        boolean connected,
        String serverName,
        String serverVersion,
        int toolCount) {
}