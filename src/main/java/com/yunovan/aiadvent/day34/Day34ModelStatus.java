package com.yunovan.aiadvent.day34;

public record Day34ModelStatus(
        String endpoint,
        String model,
        boolean available,
        String version,
        boolean installed,
        String reason) {
}
