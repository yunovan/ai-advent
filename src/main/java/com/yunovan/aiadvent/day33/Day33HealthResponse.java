package com.yunovan.aiadvent.day33;

import java.util.List;

public record Day33HealthResponse(
        String status,
        String endpoint,
        String kbDir,
        String crmDir,
        List<String> documents,
        int chunks,
        int corpusChars,
        int users,
        int tickets,
        Day33McpStatus mcp,
        Day33ModelStatus model) {
}
