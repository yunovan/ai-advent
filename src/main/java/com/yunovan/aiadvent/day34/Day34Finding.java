package com.yunovan.aiadvent.day34;

public record Day34Finding(String severity, String rule, String file, int line, String message) {

    public static Day34Finding of(String severity, String rule, String file, int line, String message) {
        return new Day34Finding(severity, rule, file, line, message);
    }
}
