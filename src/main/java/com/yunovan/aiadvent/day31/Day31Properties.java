package com.yunovan.aiadvent.day31;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day31")
public record Day31Properties(
        Integer serverPort,
        String path,
        String name,
        String version,
        String repositoryDir,
        String docsDir,
        String readmePath,
        Integer topK,
        Integer candidateK,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer gitTimeoutMs,
        Integer answerMaxTokens,
        Integer maxFiles,
        Integer maxDiffChars,
        Double temperature) {

    public static final int DEFAULT_SERVER_PORT = 9095;
    public static final String DEFAULT_PATH = "/mcp";
    public static final String DEFAULT_NAME = "ai-advent-git-mcp";
    public static final String DEFAULT_VERSION = "0.1.0";
    public static final String DEFAULT_REPOSITORY_DIR = ".";
    public static final String DEFAULT_DOCS_DIR = "docs";
    public static final String DEFAULT_README = "README.md";
    public static final int DEFAULT_TOP_K = 5;
    public static final int DEFAULT_CANDIDATE_K = 12;
    public static final int DEFAULT_CHUNK_SIZE = 700;
    public static final int DEFAULT_CHUNK_OVERLAP = 80;
    public static final int DEFAULT_GIT_TIMEOUT_MS = 5_000;
    public static final int DEFAULT_ANSWER_MAX_TOKENS = 400;
    public static final int DEFAULT_MAX_FILES = 300;
    public static final int DEFAULT_MAX_DIFF_CHARS = 6_000;
    public static final double DEFAULT_TEMPERATURE = 0.2;

    public Day31Properties {
        serverPort = serverPort == null || serverPort < 0
                ? DEFAULT_SERVER_PORT : Math.min(serverPort, 65_535);
        path = valueOr(path, DEFAULT_PATH);
        name = valueOr(name, DEFAULT_NAME);
        version = valueOr(version, DEFAULT_VERSION);
        repositoryDir = valueOr(repositoryDir, DEFAULT_REPOSITORY_DIR);
        docsDir = valueOr(docsDir, DEFAULT_DOCS_DIR);
        readmePath = valueOr(readmePath, DEFAULT_README);
        topK = clamp(topK, 1, 50, DEFAULT_TOP_K);
        candidateK = clamp(candidateK, 1, 100, DEFAULT_CANDIDATE_K);
        chunkSize = clamp(chunkSize, 100, 8_000, DEFAULT_CHUNK_SIZE);
        chunkOverlap = clamp(chunkOverlap, 0, 2_000, DEFAULT_CHUNK_OVERLAP);
        gitTimeoutMs = clamp(gitTimeoutMs, 500, 60_000, DEFAULT_GIT_TIMEOUT_MS);
        answerMaxTokens = clamp(answerMaxTokens, 1, 4_096, DEFAULT_ANSWER_MAX_TOKENS);
        maxFiles = clamp(maxFiles, 1, 10_000, DEFAULT_MAX_FILES);
        maxDiffChars = clamp(maxDiffChars, 200, 200_000, DEFAULT_MAX_DIFF_CHARS);
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
