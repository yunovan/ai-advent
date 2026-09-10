package com.yunovan.aiadvent.day09;

import java.time.Instant;

public record Day09DialogSummary(
        String dialogId,
        Instant createdAt,
        Instant finishedAt,
        String summary,
        int messageCount) {
}