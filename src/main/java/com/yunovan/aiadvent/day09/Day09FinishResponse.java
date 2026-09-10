package com.yunovan.aiadvent.day09;

import java.time.Instant;

public record Day09FinishResponse(
        String dialogId,
        Instant finishedAt,
        String summary,
        int messageCount) {
}