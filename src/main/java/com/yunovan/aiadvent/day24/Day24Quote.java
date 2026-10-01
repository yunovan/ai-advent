package com.yunovan.aiadvent.day24;

public record Day24Quote(
        String source,
        String section,
        String chunkId,
        String text,
        double score,
        int matchedKeywords) {
}