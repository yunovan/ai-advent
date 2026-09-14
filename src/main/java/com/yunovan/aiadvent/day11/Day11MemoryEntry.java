package com.yunovan.aiadvent.day11;

import java.time.Instant;

public record Day11MemoryEntry(
        String key,
        String value,
        Day11MemoryLayer layer,
        String source,
        boolean pending,
        Instant createdAt) {

    public Day11MemoryEntry {
        key = key == null ? "" : key.trim();
        value = value == null ? "" : value.trim();
        layer = layer == null ? Day11MemoryLayer.SHORT_TERM : layer;
        source = source == null || source.isBlank() ? "unknown" : source.trim();
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public Day11MemoryEntry withLayer(Day11MemoryLayer newLayer) {
        return new Day11MemoryEntry(key, value, newLayer, source, pending, createdAt);
    }

    public Day11MemoryEntry hardened() {
        return new Day11MemoryEntry(key, value, layer, source, false, createdAt);
    }

    public boolean usable() {
        return !key.isBlank() && !value.isBlank();
    }

    public boolean isCandidate() {
        return pending;
    }

    public String display() {
        return key + ": " + value;
    }
}