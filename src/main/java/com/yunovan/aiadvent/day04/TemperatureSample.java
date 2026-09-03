package com.yunovan.aiadvent.day04;

public record TemperatureSample(
        double temperature, String content, String finishReason, int words, String bestFor) {

    public static TemperatureSample from(double temperature, String content, String finishReason) {
        String text = content == null ? "" : content;
        return new TemperatureSample(temperature, text, finishReason, wordCount(text), Day04Temperatures.bestFor(temperature));
    }

    private static int wordCount(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }
}
