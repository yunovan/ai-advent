package com.yunovan.aiadvent.day13;

public enum Day13Stage {
    PLANNING("планирование"),
    EXECUTION("выполнение"),
    VALIDATION("проверка"),
    DONE("готово");

    private final String displayName;

    Day13Stage(String displayName) {
        this.displayName = displayName;
    }

    public String display() {
        return displayName;
    }

    public static Day13Stage from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        for (Day13Stage stage : values()) {
            if (stage.name().equalsIgnoreCase(normalized) || stage.displayName.equals(normalized)) {
                return stage;
            }
        }
        return null;
    }
}