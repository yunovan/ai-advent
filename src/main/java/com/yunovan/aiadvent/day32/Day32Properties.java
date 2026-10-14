package com.yunovan.aiadvent.day32;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day32")
public record Day32Properties(
        String repositoryDir,
        String baseRef,
        String headRef,
        String docsDir,
        String readmePath,
        String codeDir,
        Integer topK,
        Integer candidateK,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer gitTimeoutMs,
        Integer maxDiffChars,
        Integer maxFiles,
        Integer answerMaxTokens,
        Double temperature) {

    public static final String DEFAULT_REPOSITORY_DIR = ".";
    public static final String DEFAULT_BASE_REF = "main";
    public static final String DEFAULT_HEAD_REF = "HEAD";
    public static final String DEFAULT_DOCS_DIR = "docs";
    public static final String DEFAULT_README = "README.md";
    public static final String DEFAULT_CODE_DIR = "src/main/java";
    public static final int DEFAULT_TOP_K = 6;
    public static final int DEFAULT_CANDIDATE_K = 12;
    public static final int DEFAULT_CHUNK_SIZE = 700;
    public static final int DEFAULT_CHUNK_OVERLAP = 80;
    public static final int DEFAULT_GIT_TIMEOUT_MS = 5_000;
    public static final int DEFAULT_MAX_DIFF_CHARS = 14_000;
    public static final int DEFAULT_MAX_FILES = 100;
    public static final int DEFAULT_ANSWER_MAX_TOKENS = 700;
    public static final double DEFAULT_TEMPERATURE = 0.1;

    public Day32Properties {
        repositoryDir = valueOr(repositoryDir, DEFAULT_REPOSITORY_DIR);
        baseRef = valueOr(baseRef, DEFAULT_BASE_REF);
        headRef = valueOr(headRef, DEFAULT_HEAD_REF);
        docsDir = valueOr(docsDir, DEFAULT_DOCS_DIR);
        readmePath = valueOr(readmePath, DEFAULT_README);
        codeDir = valueOr(codeDir, DEFAULT_CODE_DIR);
        topK = clamp(topK, 1, 50, DEFAULT_TOP_K);
        candidateK = clamp(candidateK, 1, 100, DEFAULT_CANDIDATE_K);
        chunkSize = clamp(chunkSize, 100, 8_000, DEFAULT_CHUNK_SIZE);
        chunkOverlap = clamp(chunkOverlap, 0, 2_000, DEFAULT_CHUNK_OVERLAP);
        gitTimeoutMs = clamp(gitTimeoutMs, 500, 60_000, DEFAULT_GIT_TIMEOUT_MS);
        maxDiffChars = clamp(maxDiffChars, 500, 400_000, DEFAULT_MAX_DIFF_CHARS);
        maxFiles = clamp(maxFiles, 1, 10_000, DEFAULT_MAX_FILES);
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
