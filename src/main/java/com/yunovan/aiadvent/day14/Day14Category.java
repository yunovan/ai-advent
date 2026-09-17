package com.yunovan.aiadvent.day14;

public enum Day14Category {
    ARCHITECTURE("архитектура"),
    DECISION("решение"),
    STACK("стек"),
    BUSINESS("бизнес");

    private final String displayName;

    Day14Category(String displayName) {
        this.displayName = displayName;
    }

    public String display() {
        return displayName;
    }

    public static Day14Category from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase();
        for (Day14Category cat : values()) {
            if (cat.name().equalsIgnoreCase(normalized) || cat.displayName.equals(normalized)) {
                return cat;
            }
        }
        return null;
    }
}
