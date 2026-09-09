package com.yunovan.aiadvent.day07;

import java.time.Instant;

public record Day07DialogSummary(
        String dialogId,
        Instant createdAt,
        Instant finishedAt,
        String summary,
        int messageCount) {
}