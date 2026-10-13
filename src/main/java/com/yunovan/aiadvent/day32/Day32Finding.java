package com.yunovan.aiadvent.day32;

public record Day32Finding(
        String severity,
        String category,
        String file,
        int line,
        String message) {

    public static Day32Finding of(String severity, String category, String file, int line, String message) {
        return new Day32Finding(severity, category, file, line, message);
    }
}
