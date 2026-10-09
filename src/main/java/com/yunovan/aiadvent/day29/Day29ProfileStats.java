package com.yunovan.aiadvent.day29;

public record Day29ProfileStats(
        String profileId,
        String profileTitle,
        int answers,
        int fallbacks,
        Double avgCoveragePercent,
        Double avgGroundingPercent,
        Double avgLatencyMs,
        Double avgTokensPerSecond,
        Double avgInputTokens,
        Double avgOutputTokens) {
}
