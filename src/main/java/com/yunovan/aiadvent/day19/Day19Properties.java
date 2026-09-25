package com.yunovan.aiadvent.day19;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day19")
public record Day19Properties(
        Integer serverPort,
        String path,
        String name,
        String version,
        String storeDir) {

    public static final int DEFAULT_SERVER_PORT = 9092;
    public static final String DEFAULT_PATH = "/mcp";
    public static final String DEFAULT_NAME = "ai-advent-pipeline-mcp";
    public static final String DEFAULT_VERSION = "0.1.0";
    public static final String DEFAULT_STORE_DIR = "data/day19-market";

    public Day19Properties {
        serverPort = serverPort == null || serverPort < 0 ? DEFAULT_SERVER_PORT : serverPort;
        path = valueOr(path, DEFAULT_PATH);
        name = valueOr(name, DEFAULT_NAME);
        version = valueOr(version, DEFAULT_VERSION);
        storeDir = valueOr(storeDir, DEFAULT_STORE_DIR);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}