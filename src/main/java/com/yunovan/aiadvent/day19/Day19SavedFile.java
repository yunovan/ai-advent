package com.yunovan.aiadvent.day19;

public record Day19SavedFile(
        String fileName,
        String path,
        String format,
        long bytes,
        String content) {
}