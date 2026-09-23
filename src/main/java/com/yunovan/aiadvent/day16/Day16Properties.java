package com.yunovan.aiadvent.day16;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day16")
public record Day16Properties(
        Integer serverPort,
        String path,
        String name,
        String version) {

    public static final int DEFAULT_SERVER_PORT = 8090;
    public static final String DEFAULT_PATH = "/mcp";
    public static final String DEFAULT_NAME = "ai-advent-mcp";
    public static final String DEFAULT_VERSION = "0.1.0";

    public Day16Properties {
        serverPort = serverPort == null || serverPort < 0 ? DEFAULT_SERVER_PORT : serverPort;
        path = valueOr(path, DEFAULT_PATH);
        name = valueOr(name, DEFAULT_NAME);
        version = valueOr(version, DEFAULT_VERSION);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}