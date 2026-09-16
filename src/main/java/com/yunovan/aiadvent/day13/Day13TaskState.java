package com.yunovan.aiadvent.day13;

import java.time.Instant;
import java.util.List;

public record Day13TaskState(
        String taskId,
        String title,
        Day13Stage stage,
        int step,
        String expectedAction,
        boolean paused,
        List<String> notes,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt) {
}