package com.yunovan.aiadvent.day05;

public enum ModelTier {
    WEAK("weak", "Слабая · 3B"),
    MEDIUM("medium", "Средняя · 7B"),
    STRONG("strong", "Сильная · 70B");

    private final String id;
    private final String label;

    ModelTier(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String id() {
        return id;
    }

    public String label() {
        return label;
    }
}
