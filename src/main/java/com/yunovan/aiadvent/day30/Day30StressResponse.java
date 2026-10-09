package com.yunovan.aiadvent.day30;

import java.util.List;

public record Day30StressResponse(
        int requests,
        int concurrency,
        int succeeded,
        int failed,
        int rateLimited,
        long totalMs,
        double throughputPerSecond,
        double avgLatencyMs,
        long minLatencyMs,
        long maxLatencyMs,
        List<Day30StressItem> items,
        String verdict) {
}
