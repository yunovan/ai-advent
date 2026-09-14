package com.yunovan.aiadvent.day11;

import java.math.BigDecimal;
import java.util.List;

public record Day11MetricsReport(
        String dialogId,
        long contextLimit,
        BigDecimal inputPrice,
        BigDecimal outputPrice,
        long totalTokens,
        long fullTotalTokens,
        BigDecimal totalCostUsd,
        BigDecimal fullTotalCostUsd,
        int messageCount,
        int workingCount,
        int longTermCount,
        List<Day11MetricsTurn> turns) {
}
