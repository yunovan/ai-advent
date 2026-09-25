package com.yunovan.aiadvent.day19;

public record Day19HealthResponse(
        boolean connected,
        String serverName,
        String serverVersion,
        int toolCount) {
}