package com.yunovan.aiadvent.day26;

public record Day26ModelInfo(String model, String format, String parameterSize,
                             String quantizationLevel, long parameterCount,
                             long contextLength, String reason) {

    public static Day26ModelInfo unavailable(String reason) {
        return new Day26ModelInfo("", "", "", "", 0, 0, reason);
    }
}
