package com.yunovan.aiadvent.day22;

import java.util.List;

public record Day22EvalItem(
        String id,
        String question,
        List<String> expectedKeywords,
        List<String> expectedSources,
        List<String> retrievedSources,
        boolean retrievalHit,
        double ragCoveragePercent,
        double plainCoveragePercent,
        double coverageGapPercent) {
}