package com.yunovan.aiadvent.day15;

public enum Day15Stage {
    PLANNING("планирование"),
    PLAN_APPROVED("план утверждён"),
    EXECUTION("выполнение"),
    VALIDATION("проверка"),
    DONE("готово");

    private final String displayName;

    Day15Stage(String displayName) {
        this.displayName = displayName;
    }

    public String display() {
        return displayName;
    }

    public static Day15Stage from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        for (Day15Stage stage : values()) {
            if (stage.name().equalsIgnoreCase(normalized) || stage.displayName.equals(normalized)) {
                return stage;
            }
        }
        return null;
    }
}