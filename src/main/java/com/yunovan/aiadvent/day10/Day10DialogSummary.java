package com.yunovan.aiadvent.day10;

import java.math.BigDecimal;
import java.time.Instant;

public record Day10DialogSummary(
        String dialogId,
        Instant createdAt,
        Instant finishedAt,
        String summary,
        Day10Strategy strategy,
        int windowSize,
        int messageCount,
        int factsCount,
        int branchCount,
        long totalTokens,
        long fullTotalTokens,
        BigDecimal totalCostUsd,
        BigDecimal fullTotalCostUsd) {
}