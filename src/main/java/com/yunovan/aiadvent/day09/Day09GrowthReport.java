package com.yunovan.aiadvent.day09;

import java.math.BigDecimal;
import java.util.List;

public record Day09GrowthReport(
        String dialogId,
        long contextLimit,
        BigDecimal inputPriceUsdPerM,
        BigDecimal outputPriceUsdPerM,
        long totalTokens,
        long fullTotalTokens,
        long savedTokens,
        BigDecimal totalCostUsd,
        BigDecimal fullTotalCostUsd,
        List<Day09GrowthTurn> turns) {
}