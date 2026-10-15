package com.yunovan.aiadvent.day34;

import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day34")
public record Day34Properties(
        Integer serverPort,
        String path,
        String name,
        String version,
        String workspaceDir,
        String codeDir,
        String docsDir,
        String readmePath,
        String outputDir,
        Integer topK,
        Integer candidateK,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer maxFiles,
        Integer maxFileBytes,
        Integer maxReadLines,
        Integer searchMaxResults,
        Integer answerMaxTokens,
        Double temperature) {

    public static final int DEFAULT_SERVER_PORT = 9097;
    public static final String DEFAULT_PATH = "/mcp";
    public static final String DEFAULT_NAME = "ai-advent-file-mcp";
    public static final String DEFAULT_VERSION = "0.1.0";
    public static final String DEFAULT_WORKSPACE_DIR = ".";
    public static final String DEFAULT_CODE_DIR = "src/main/java";
    public static final String DEFAULT_DOCS_DIR = "docs";
    public static final String DEFAULT_README = "README.md";
    public static final String DEFAULT_OUTPUT_DIR = "build/day34-output";
    public static final int DEFAULT_TOP_K = 6;
    public static final int DEFAULT_CANDIDATE_K = 12;
    public static final int DEFAULT_CHUNK_SIZE = 700;
    public static final int DEFAULT_CHUNK_OVERLAP = 80;
    public static final int DEFAULT_MAX_FILES = 200;
    public static final int DEFAULT_MAX_FILE_BYTES = 200_000;
    public static final int DEFAULT_MAX_READ_LINES = 400;
    public static final int DEFAULT_SEARCH_MAX_RESULTS = 100;
    public static final int DEFAULT_ANSWER_MAX_TOKENS = 700;
    public static final double DEFAULT_TEMPERATURE = 0.2;

    public static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "java", "kt", "kts", "gradle", "md", "markdown", "txt", "rst", "adoc",
            "yml", "yaml", "xml", "properties", "sql", "js", "ts", "json", "html", "css");

    public Day34Properties {
        serverPort = serverPort == null || serverPort < 0
                ? DEFAULT_SERVER_PORT : Math.min(serverPort, 65_535);
        path = valueOr(path, DEFAULT_PATH);
        name = valueOr(name, DEFAULT_NAME);
        version = valueOr(version, DEFAULT_VERSION);
        workspaceDir = valueOr(workspaceDir, DEFAULT_WORKSPACE_DIR);
        codeDir = valueOr(codeDir, DEFAULT_CODE_DIR);
        docsDir = valueOr(docsDir, DEFAULT_DOCS_DIR);
        readmePath = valueOr(readmePath, DEFAULT_README);
        outputDir = valueOr(outputDir, DEFAULT_OUTPUT_DIR);
        topK = clamp(topK, 1, 50, DEFAULT_TOP_K);
        candidateK = clamp(candidateK, 1, 100, DEFAULT_CANDIDATE_K);
        chunkSize = clamp(chunkSize, 100, 8_000, DEFAULT_CHUNK_SIZE);
        chunkOverlap = clamp(chunkOverlap, 0, 2_000, DEFAULT_CHUNK_OVERLAP);
        maxFiles = clamp(maxFiles, 1, 10_000, DEFAULT_MAX_FILES);
        maxFileBytes = clamp(maxFileBytes, 1_000, 5_000_000, DEFAULT_MAX_FILE_BYTES);
        maxReadLines = clamp(maxReadLines, 50, 20_000, DEFAULT_MAX_READ_LINES);
        searchMaxResults = clamp(searchMaxResults, 1, 5_000, DEFAULT_SEARCH_MAX_RESULTS);
        answerMaxTokens = clamp(answerMaxTokens, 1, 4_096, DEFAULT_ANSWER_MAX_TOKENS);
        temperature = temperature == null || temperature.isNaN()
                ? DEFAULT_TEMPERATURE : Math.max(0.0, Math.min(2.0, temperature));
    }

    public static boolean isAllowed(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return false;
        }
        return ALLOWED_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
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
