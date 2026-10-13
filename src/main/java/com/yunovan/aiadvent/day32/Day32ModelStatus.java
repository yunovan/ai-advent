package com.yunovan.aiadvent.day32;

public record Day32ModelStatus(
        String endpoint,
        String model,
        boolean available,
        String version,
        boolean installed,
        String reason) {
}
