package com.yunovan.aiadvent.day29;

import java.util.List;

public record Day29AnswerResponse(
        String profileId,
        String profileTitle,
        String question,
        String answer,
        boolean fallback,
        Double groundingPercent,
        List<String> sources,
        String matchedQuery,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond,
        String unavailableReason) {
}
