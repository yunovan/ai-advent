package com.yunovan.aiadvent.day10;

public record Day10Fact(String key, String value, boolean active) {

    public Day10Fact {
        key = key == null ? "" : key.trim();
        value = value == null ? "" : value.trim();
    }

    public Day10Fact withValue(String newValue) {
        return new Day10Fact(key, newValue == null ? "" : newValue.trim(), active);
    }

    public Day10Fact withActive(boolean newActive) {
        return new Day10Fact(key, value, newActive);
    }

    public boolean usable() {
        return active && !key.isBlank();
    }

    public String display() {
        return key + ": " + value;
    }
}