package com.yunovan.aiadvent.day24;

import java.util.List;

public record Day24GroundedResponse(
        String question,
        String matchedQuery,
        boolean rewritten,
        int candidatesBefore,
        int filteredOut,
        List<Day24Source> sources,
        List<Day24Quote> quotes,
        String answer,
        double bestScore,
        double supportCoveragePercent,
        boolean supported,
        boolean unknown,
        boolean fallback) {
}