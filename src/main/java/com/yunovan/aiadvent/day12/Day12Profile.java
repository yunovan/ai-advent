package com.yunovan.aiadvent.day12;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Day12Profile(
        String id,
        String name,
        String style,
        String format,
        List<String> restrictions,
        String notes,
        Instant createdAt) {

    public Day12Profile {
        id = id == null ? "" : id.trim();
        name = name == null ? "" : name.trim();
        style = style == null ? "" : style.trim();
        format = format == null ? "" : format.trim();
        restrictions = restrictions == null
                ? List.of()
                : restrictions.stream().filter(Objects::nonNull).map(String::trim).filter(r -> !r.isBlank()).toList();
        notes = notes == null ? "" : notes.trim();
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    public boolean usable() {
        return !id.isBlank() && !name.isBlank();
    }

    public String display() {
        StringBuilder builder = new StringBuilder(name);
        if (!style.isBlank()) {
            builder.append(" · ").append(style);
        }
        if (!format.isBlank()) {
            builder.append(" · ").append(format);
        }
        if (!restrictions.isEmpty()) {
            builder.append(" · без: ").append(String.join(", ", restrictions));
        }
        return builder.toString();
    }
}