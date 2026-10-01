package com.yunovan.aiadvent.day21;

public record Day21StrategyMetric(
        String strategy,
        int documents,
        int chunks,
        int corpusChars,
        double avgChunkChars,
        double minChunkChars,
        double maxChunkChars,
        double stddev,
        double coefficientOfVariation,
        int probeHits,
        int probeTotal,
        double probeCoveragePercent) {
}