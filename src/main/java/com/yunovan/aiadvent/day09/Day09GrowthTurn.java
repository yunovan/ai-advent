package com.yunovan.aiadvent.day09;

import java.math.BigDecimal;

public record Day09GrowthTurn(
        int turn,
        long promptTokens,
        long fullPromptTokens,
        long responseTokens,
        long cumulativeTokens,
        long cumulativeFullTokens,
        BigDecimal turnCostUsd,
        BigDecimal cumulativeCostUsd,
        BigDecimal cumulativeFullCostUsd) {
}