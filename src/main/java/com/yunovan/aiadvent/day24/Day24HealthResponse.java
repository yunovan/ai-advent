package com.yunovan.aiadvent.day24;

import java.util.List;

public record Day24HealthResponse(
        int documents,
        long corpusChars,
        int pagesEstimate,
        String strategy,
        int topKBefore,
        int topKAfter,
        double threshold,
        double unknownThreshold,
        boolean rewrite,
        int quotesPerSource,
        int quoteMinChars,
        double supportThreshold,
        int answerMaxTokens,
        List<String> weakQuestions,
        List<String> strategies) {
}