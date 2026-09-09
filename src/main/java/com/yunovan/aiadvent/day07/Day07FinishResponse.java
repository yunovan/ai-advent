package com.yunovan.aiadvent.day07;

import java.time.Instant;

public record Day07FinishResponse(
        String dialogId,
        Instant finishedAt,
        String summary,
        int messageCount) {
}