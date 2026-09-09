package com.yunovan.aiadvent.day08;

import java.math.BigDecimal;
import java.util.List;

public record Day08GrowthReport(
        String dialogId,
        long contextLimit,
        BigDecimal inputPriceUsdPerM,
        BigDecimal outputPriceUsdPerM,
        long totalTokens,
        BigDecimal totalCostUsd,
        List<Day08GrowthTurn> turns,
        List<Day08DialogComparison> previousDialogs) {
}