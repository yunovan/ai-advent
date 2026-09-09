package com.yunovan.aiadvent.day08;

import java.math.BigDecimal;
import java.time.Instant;

/** Итоги завершённого диалога: суммарные токены и стоимость, чтобы сравнить с текущим диалогом. */
public record Day08DialogComparison(
        String dialogId,
        Instant finishedAt,
        String summary,
        int messageCount,
        int turnCount,
        long totalTokens,
        BigDecimal totalCostUsd) {
}