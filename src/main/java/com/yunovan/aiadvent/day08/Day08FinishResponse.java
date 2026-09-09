package com.yunovan.aiadvent.day08;

import java.time.Instant;

public record Day08FinishResponse(
        String dialogId,
        Instant finishedAt,
        String summary,
        int messageCount) {
}