package com.yunovan.aiadvent.day11;

import java.time.Instant;

public record Day11FinishResponse(
        String dialogId,
        Instant finishedAt,
        String summary,
        int messageCount,
        int longTermEntryCount) {
}
