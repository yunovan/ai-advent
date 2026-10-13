package com.yunovan.aiadvent.day31;

import java.util.List;

public record Day31HealthResponse(
        String status,
        String endpoint,
        String repositoryDir,
        String branch,
        boolean dirty,
        List<String> documents,
        int chunks,
        int corpusChars,
        Day31McpStatus mcp,
        Day31ModelStatus model) {
}
