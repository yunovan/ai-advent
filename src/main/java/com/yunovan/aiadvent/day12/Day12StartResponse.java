package com.yunovan.aiadvent.day12;

import java.time.Instant;
import java.util.List;

public record Day12StartResponse(
        String dialogId,
        Instant createdAt,
        Day12Profile profile,
        List<Day12Profile> profiles) {
}