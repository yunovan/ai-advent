package com.yunovan.aiadvent.day15;

import java.time.Instant;
import java.util.List;

public record Day15TaskState(
        String taskId,
        String title,
        Day15Stage stage,
        List<Day15Stage> allowedTransitions,
        int step,
        String expectedAction,
        boolean paused,
        List<String> notes,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt) {
}