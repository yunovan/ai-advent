package com.yunovan.aiadvent.day11;

import java.time.Instant;

public record Day11StartResponse(
        String dialogId,
        Instant createdAt,
        Day11MemoryLayer[] layers) {
}
