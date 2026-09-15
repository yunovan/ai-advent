package com.yunovan.aiadvent.day12;

import java.time.Instant;

public record Day12FinishResponse(
        String dialogId,
        Instant finishedAt,
        String summary,
        int messageCount,
        String profileId,
        int longTermEntryCount) {
}