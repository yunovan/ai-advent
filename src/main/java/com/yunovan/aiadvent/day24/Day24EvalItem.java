package com.yunovan.aiadvent.day24;

import java.util.List;

public record Day24EvalItem(
        String id,
        String question,
        boolean hasSources,
        int sourcesCount,
        boolean hasQuotes,
        int quotesCount,
        boolean supported,
        double supportCoveragePercent,
        boolean unknown,
        List<String> sources) {
}