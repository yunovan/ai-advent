package com.yunovan.aiadvent.day30;

public record Day30Stats(
        long totalRequests,
        long acceptedRequests,
        long rejectedByRateLimit,
        long rejectedByContext,
        long rejectedByConcurrency,
        int activeRequests,
        int peakActiveRequests,
        int sessions,
        Double avgLatencyMs,
        Double avgTokensPerSecond) {
}
