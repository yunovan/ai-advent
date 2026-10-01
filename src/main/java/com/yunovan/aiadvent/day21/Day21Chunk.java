package com.yunovan.aiadvent.day21;

public record Day21Chunk(
        String strategy,
        String source,
        String fileName,
        String title,
        String section,
        String chunkId,
        int startOffset,
        int charCount,
        String text) {
}