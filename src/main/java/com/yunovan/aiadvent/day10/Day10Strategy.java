package com.yunovan.aiadvent.day10;

import com.fasterxml.jackson.annotation.JsonValue;

public enum Day10Strategy {

    SLIDING_WINDOW("sliding", "Sliding Window",
            "в модели только последние N сообщений, всё остальное отбрасывается"),
    FACTS("facts", "Sticky Facts",
            "блок фактов «ключ-значение» + последние N сообщений"),
    BRANCHING("branching", "Branching",
            "ветки диалога от checkpoint'а, каждая живёт независимо");

    private final String key;
    private final String label;
    private final String description;

    Day10Strategy(String key, String label, String description) {
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

    public static Day10Strategy from(String value) {
        if (value == null || value.isBlank()) {
            return SLIDING_WINDOW;
        }
        String normalized = value.trim().toLowerCase();
        for (Day10Strategy strategy : values()) {
            if (strategy.key.equals(normalized)) {
                return strategy;
            }
        }
        throw new IllegalArgumentException(
                "Неизвестная стратегия '" + value + "'. Доступные: sliding, facts, branching");
    }
}