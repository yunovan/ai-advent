package com.yunovan.aiadvent.day08;

import java.math.BigDecimal;

public record Day08GrowthTurn(
        int turn,
        long promptTokens,
        long responseTokens,
        long cumulativeTokens,
        BigDecimal turnCostUsd,
        BigDecimal cumulativeCostUsd) {
}