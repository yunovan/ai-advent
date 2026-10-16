package com.yunovan.aiadvent.day34;

public record Day34FileContent(
        String path,
        int startLine,
        int endLine,
        int totalLines,
        String content,
        boolean truncated) {
}
