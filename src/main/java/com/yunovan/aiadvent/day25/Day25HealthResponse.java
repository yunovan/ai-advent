package com.yunovan.aiadvent.day25;

import java.util.List;

public record Day25HealthResponse(
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
        int historyLimit,
        int maxSessions,
        int memoryTermsLimit,
        int sessions,
        List<String> scenarios,
        List<String> strategies) {
}
