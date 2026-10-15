package com.yunovan.aiadvent.day34;

public record Day34HealthResponse(
        String status,
        String workspaceDir,
        boolean workspaceAvailable,
        int codeFiles,
        int documents,
        int chunks,
        int corpusChars,
        Day34McpStatus mcp,
        Day34ModelStatus model) {
}
