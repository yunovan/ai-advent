package com.yunovan.aiadvent.day29;

public record Day29ModelReport(
        String endpoint,
        String model,
        boolean available,
        boolean installed,
        String version,
        String format,
        String parameterSize,
        String quantizationLevel,
        long parameterCount,
        long contextLength,
        long modelSizeBytes,
        long loadedMemoryBytes,
        String reason) {

    public static Day29ModelReport unavailable(String endpoint, String model, String reason) {
        return new Day29ModelReport(endpoint, model, false, false, "", "", "", "",
                0, 0, 0, 0, reason);
    }
}
