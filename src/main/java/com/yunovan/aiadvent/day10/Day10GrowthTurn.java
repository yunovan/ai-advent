package com.yunovan.aiadvent.day10;

import java.math.BigDecimal;

public record Day10GrowthTurn(
        int turn,
        long promptTokens,
        long responseTokens,
        long cumulativeTokens,
        BigDecimal turnCostUsd,
        BigDecimal cumulativeCostUsd) {
}