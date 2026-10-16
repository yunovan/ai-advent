package com.yunovan.aiadvent.day35;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day35")
public record Day35Properties(Integer serverPort, String path, String name, String version, Integer maxProblems, Double temperature, Integer answerMaxTokens) {

    public static final int DEFAULT_SERVER_PORT = 8098;
    public static final String DEFAULT_PATH = "/api/day35";
    public static final String DEFAULT_NAME = "ai-advent-math-tutor";
    public static final String DEFAULT_VERSION = "0.1.0";
    public static final int DEFAULT_MAX_PROBLEMS = 5;
    public static final double DEFAULT_TEMPERATURE = 0.3;
    public static final int DEFAULT_ANSWER_MAX_TOKENS = 600;

    public Day35Properties {
        serverPort = serverPort == null || serverPort < 0 ? DEFAULT_SERVER_PORT : Math.min(serverPort, 65535);
        path = (path == null || path.isBlank()) ? DEFAULT_PATH : path.trim();
        name = (name == null || name.isBlank()) ? DEFAULT_NAME : name.trim();
        version = (version == null || version.isBlank()) ? DEFAULT_VERSION : version.trim();
        maxProblems = maxProblems == null || maxProblems < 1 ? DEFAULT_MAX_PROBLEMS : Math.min(maxProblems, 20);
        temperature = temperature == null || temperature.isNaN() || temperature < 0 ? DEFAULT_TEMPERATURE : Math.min(temperature, 2.0);
        answerMaxTokens = answerMaxTokens == null || answerMaxTokens < 1 ? DEFAULT_ANSWER_MAX_TOKENS : Math.min(answerMaxTokens, 1024);
    }
}