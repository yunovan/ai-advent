package com.yunovan.aiadvent.day21;

import java.util.List;

public record Day21HealthResponse(
        String serverName,
        String version,
        int documents,
        int corpusChars,
        int pagesEstimate,
        List<Day21StrategyInfo> strategies) {
}