package com.yunovan.aiadvent.day26;

import java.util.List;

public record Day26RunReport(
        String endpoint,
        String model,
        List<Day26TaskResult> results,
        int okCount,
        int failureCount,
        long totalLatencyMs,
        String verdict) {

    public Day26RunReport {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public int total() {
        return results.size();
    }
}
