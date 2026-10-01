package com.yunovan.aiadvent.day23;

import java.util.List;

public record Day23EvalResponse(
        int total,
        int baseRetrievalHits,
        int fullRetrievalHits,
        double baseRecallPercent,
        double fullRecallPercent,
        int totalFilteredOut,
        double avgCoverageBasePercent,
        double avgCoverageFullPercent,
        double coverageGapPercent,
        int improved,
        String verdict,
        List<Day23EvalItem> items) {
}