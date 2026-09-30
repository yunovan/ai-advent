package com.yunovan.aiadvent.day21;

import java.util.List;

public record Day21ComparisonResponse(
        List<Day21StrategyMetric> strategies,
        List<String> probes,
        String verdict) {
}