package com.yunovan.aiadvent.day18;

public record Day18HealthResponse(
        boolean connected,
        String serverName,
        String serverVersion,
        int toolCount) {
}