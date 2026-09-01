package com.yunovan.aiadvent.day02;

public record CompareSample(String content, String finishReason, int characters, int words) {

    public static CompareSample from(String content, String finishReason) {
        String text = content == null ? "" : content;
        return new CompareSample(text, finishReason, text.length(), wordCount(text));
    }

    private static int wordCount(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }
}
