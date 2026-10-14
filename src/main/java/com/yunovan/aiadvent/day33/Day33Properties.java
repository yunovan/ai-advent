package com.yunovan.aiadvent.day33;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day33")
public record Day33Properties(
        Integer serverPort,
        String path,
        String name,
        String version,
        String kbDir,
        String crmDir,
        Integer topK,
        Integer candidateK,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer answerMaxTokens,
        Double temperature) {

    public static final int DEFAULT_SERVER_PORT = 9096;
    public static final String DEFAULT_PATH = "/mcp";
    public static final String DEFAULT_NAME = "ai-advent-crm-mcp";
    public static final String DEFAULT_VERSION = "0.1.0";
    public static final String DEFAULT_KB_DIR = "data/day33-kb";
    public static final String DEFAULT_CRM_DIR = "data/day33-crm";
    public static final int DEFAULT_TOP_K = 5;
    public static final int DEFAULT_CANDIDATE_K = 12;
    public static final int DEFAULT_CHUNK_SIZE = 700;
    public static final int DEFAULT_CHUNK_OVERLAP = 80;
    public static final int DEFAULT_ANSWER_MAX_TOKENS = 400;
    public static final double DEFAULT_TEMPERATURE = 0.2;

    public Day33Properties {
        serverPort = serverPort == null || serverPort < 0
                ? DEFAULT_SERVER_PORT : Math.min(serverPort, 65_535);
        path = valueOr(path, DEFAULT_PATH);
        name = valueOr(name, DEFAULT_NAME);
        version = valueOr(version, DEFAULT_VERSION);
        kbDir = valueOr(kbDir, DEFAULT_KB_DIR);
        crmDir = valueOr(crmDir, DEFAULT_CRM_DIR);
        topK = clamp(topK, 1, 50, DEFAULT_TOP_K);
        candidateK = clamp(candidateK, 1, 100, DEFAULT_CANDIDATE_K);
        chunkSize = clamp(chunkSize, 100, 8_000, DEFAULT_CHUNK_SIZE);
        chunkOverlap = clamp(chunkOverlap, 0, 2_000, DEFAULT_CHUNK_OVERLAP);
        answerMaxTokens = clamp(answerMaxTokens, 1, 4_096, DEFAULT_ANSWER_MAX_TOKENS);
        temperature = temperature == null || temperature.isNaN()
                ? DEFAULT_TEMPERATURE : Math.max(0.0, Math.min(2.0, temperature));
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        if (value == null || value < min) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
