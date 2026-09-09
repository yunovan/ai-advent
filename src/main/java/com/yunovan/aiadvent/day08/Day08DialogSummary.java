package com.yunovan.aiadvent.day08;

import java.time.Instant;

public record Day08DialogSummary(
        String dialogId,
        Instant createdAt,
        Instant finishedAt,
        String summary,
        int messageCount) {
}