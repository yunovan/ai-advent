package com.yunovan.aiadvent.day29;

public record Day29RunResponse(
        int questions,
        int runs,
        Day29ProfileStats baseline,
        Day29ProfileStats tuned,
        long loadedMemoryBytes,
        String qualityVerdict,
        String speedVerdict,
        String resourceVerdict) {
}
