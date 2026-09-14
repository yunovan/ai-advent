package com.yunovan.aiadvent.day11;

import java.math.BigDecimal;

public record Day11MetricsTurn(
        int turn,
        long promptTokens,
        long cumulativeTokens,
        long cumulativeFullTokens,
        BigDecimal turnCost,
        BigDecimal cumulativeCost,
        BigDecimal cumulativeFullCost) {
}
