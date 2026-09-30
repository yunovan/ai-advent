package com.yunovan.aiadvent.day23;

import java.util.List;

public record Day23EvalItem(
        String id,
        String question,
        List<String> expectedKeywords,
        List<String> expectedSources,
        List<String> baseSources,
        boolean baseHit,
        double baseCoveragePercent,
        int candidatesBefore,
        List<String> fullSources,
        boolean fullHit,
        double fullCoveragePercent,
        int filteredOut) {
}