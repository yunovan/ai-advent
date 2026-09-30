package com.yunovan.aiadvent.day21;

public record Day21IngestResponse(
        String strategy,
        int documents,
        int chunks,
        int corpusChars,
        String indexFile) {
}