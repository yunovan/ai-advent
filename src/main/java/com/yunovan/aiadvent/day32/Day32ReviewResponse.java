package com.yunovan.aiadvent.day32;

import java.util.List;

public record Day32ReviewResponse(
        String base,
        String head,
        String title,
        int filesChanged,
        List<Day32ChangedFile> files,
        int diffChars,
        int addedLines,
        int removedLines,
        boolean diffTruncated,
        List<Day32Finding> bugs,
        List<Day32Finding> architecture,
        List<Day32Finding> recommendations,
        String summary,
        String review,
        boolean fallback,
        String model,
        int retrieved,
        List<String> sources,
        List<String> contextFiles,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond,
        String note) {
}
