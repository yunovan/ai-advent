package com.yunovan.aiadvent.day28;

public record Day28EngineStats(
        boolean available,
        String reason,
        int answered,
        int fallbacks,
        Double avgCoveragePercent,
        Double coverageStdDev,
        Double avgLatencyMs,
        Double latencyStdDevMs,
        Double avgTokensPerSecond) {

    public Day28EngineStats {
        reason = reason == null ? "" : reason;
    }

    public static Day28EngineStats unavailable(String reason) {
        return new Day28EngineStats(false, reason, 0, 0,
                null, null, null, null, null);
    }
}
