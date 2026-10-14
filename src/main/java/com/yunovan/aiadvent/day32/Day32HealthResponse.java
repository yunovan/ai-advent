package com.yunovan.aiadvent.day32;

public record Day32HealthResponse(
        String status,
        String repositoryDir,
        String branch,
        String baseRef,
        int documents,
        int codeFiles,
        int chunks,
        int corpusChars,
        Day32ModelStatus model) {
}
