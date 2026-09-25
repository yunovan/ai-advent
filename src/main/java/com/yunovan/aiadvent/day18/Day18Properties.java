package com.yunovan.aiadvent.day18;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day18")
public record Day18Properties(
        Integer serverPort,
        String path,
        String name,
        String version,
        String storeDir,
        Long tickMillis) {

    public static final int DEFAULT_SERVER_PORT = 9091;
    public static final String DEFAULT_PATH = "/mcp";
    public static final String DEFAULT_NAME = "ai-advent-scheduler-mcp";
    public static final String DEFAULT_VERSION = "0.1.0";
    public static final String DEFAULT_STORE_DIR = "data/day18-scheduler";
    public static final long DEFAULT_TICK_MILLIS = 500;

    public Day18Properties {
        serverPort = serverPort == null || serverPort < 0 ? DEFAULT_SERVER_PORT : serverPort;
        path = valueOr(path, DEFAULT_PATH);
        name = valueOr(name, DEFAULT_NAME);
        version = valueOr(version, DEFAULT_VERSION);
        storeDir = valueOr(storeDir, DEFAULT_STORE_DIR);
        tickMillis = tickMillis == null || tickMillis <= 0 ? DEFAULT_TICK_MILLIS : tickMillis;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}