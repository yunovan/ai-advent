package com.yunovan.aiadvent.day14;

import java.time.Instant;
import java.util.UUID;

public record Day14Invariant(
        String id,
        Day14Category category,
        String title,
        String description,
        boolean active,
        Instant createdAt) {

    public static Day14Invariant create(Day14Category category, String title, String description) {
        return new Day14Invariant(
                UUID.randomUUID().toString(),
                category,
                title.trim(),
                description.trim(),
                true,
                Instant.now());
    }

    public Day14Invariant withActive(boolean value) {
        return new Day14Invariant(id, category, title, description, value, createdAt);
    }
}