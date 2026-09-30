package com.yunovan.aiadvent.day21;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day21")
public record Day21Properties(
        Integer serverPort,
        String path,
        String name,
        String version,
        String storeDir,
        String corpusDir,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer dimensions) {

    public static final int DEFAULT_SERVER_PORT = 9094;
    public static final String DEFAULT_PATH = "/mcp";
    public static final String DEFAULT_NAME = "ai-advent-index-mcp";
    public static final String DEFAULT_VERSION = "0.1.0";
    public static final String DEFAULT_STORE_DIR = "data/day21-index";
    public static final String DEFAULT_CORPUS_DIR = "data/day21-corpus";
    public static final int DEFAULT_CHUNK_SIZE = 600;
    public static final int DEFAULT_CHUNK_OVERLAP = 80;
    public static final int DEFAULT_DIMENSIONS = 512;

    public static final String STRATEGY_FIXED = "fixed";
    public static final String STRATEGY_STRUCTURAL = "structural";

    public Day21Properties {
        serverPort = serverPort == null || serverPort < 0 ? DEFAULT_SERVER_PORT : serverPort;
        path = valueOr(path, DEFAULT_PATH);
        name = valueOr(name, DEFAULT_NAME);
        version = valueOr(version, DEFAULT_VERSION);
        storeDir = valueOr(storeDir, DEFAULT_STORE_DIR);
        corpusDir = valueOr(corpusDir, DEFAULT_CORPUS_DIR);
        chunkSize = chunkSize == null || chunkSize <= 0 ? DEFAULT_CHUNK_SIZE : chunkSize;
        chunkOverlap = chunkOverlap == null || chunkOverlap < 0 ? DEFAULT_CHUNK_OVERLAP : chunkOverlap;
        dimensions = dimensions == null || dimensions <= 0 ? DEFAULT_DIMENSIONS : dimensions;
    }

    public boolean isValidStrategy(String strategy) {
        return STRATEGY_FIXED.equals(strategy) || STRATEGY_STRUCTURAL.equals(strategy);
    }

    public String normalizeStrategy(String strategy) {
        String candidate = strategy == null ? STRATEGY_FIXED : strategy.trim();
        if (candidate.equals(STRATEGY_STRUCTURAL)) {
            return STRATEGY_STRUCTURAL;
        }
        return STRATEGY_FIXED;
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}