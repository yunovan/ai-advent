package com.yunovan.aiadvent.day11;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Day11MemoryLayer {

    SHORT_TERM("short-term", "Краткосрочная", "текущий диалог: сообщения, которые агент видит сейчас"),
    WORKING("working", "Рабочая", "данные текущей задачи: цель, ограничения, требования, промежуточные решения"),
    LONG_TERM("long-term", "Долговременная", "профиль пользователя, принятые решения, накопленные знания — живут между задачами");

    private final String key;
    private final String label;
    private final String description;

    Day11MemoryLayer(String key, String label, String description) {
        this.key = key;
        this.label = label;
        this.description = description;
    }

    @JsonValue
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public static Day11MemoryLayer from(String value) {
        if (value == null || value.isBlank()) {
            return SHORT_TERM;
        }
        String normalized = value.trim().toLowerCase();
        for (Day11MemoryLayer layer : values()) {
            if (layer.key.equals(normalized)) {
                return layer;
            }
        }
        throw new IllegalArgumentException(
                "Неизвестный слой памяти '" + value + "'. Доступные: short-term, working, long-term");
    }
}