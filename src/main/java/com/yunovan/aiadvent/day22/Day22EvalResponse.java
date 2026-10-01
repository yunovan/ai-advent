package com.yunovan.aiadvent.day22;

import java.util.List;

public record Day22EvalResponse(
        int total,
        int retrievalHits,
        double retrievalRecallPercent,
        double ragAvgCoveragePercent,
        double plainAvgCoveragePercent,
        double avgCoverageGapPercent,
        String verdict,
        List<Day22EvalItem> items) {
}