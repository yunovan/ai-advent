package com.yunovan.aiadvent.day10;

import java.math.BigDecimal;
import java.util.List;

public record Day10GrowthReport(
        String dialogId,
        Day10Strategy strategy,
        long contextLimit,
        BigDecimal inputPriceUsdPerM,
        BigDecimal outputPriceUsdPerM,
        long totalTokens,
        long fullTotalTokens,
        BigDecimal totalCostUsd,
        BigDecimal fullTotalCostUsd,
        int windowSize,
        int factsCount,
        int branchCount,
        String activeBranchId,
        List<Day10GrowthTurn> turns) {
}