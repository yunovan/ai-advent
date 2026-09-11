package com.yunovan.aiadvent.day10;

import java.time.Instant;

public record Day10FinishResponse(
        String dialogId,
        Instant finishedAt,
        String summary,
        int messageCount) {
}