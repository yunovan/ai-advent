package com.yunovan.aiadvent.day33;

public record Day33ModelStatus(
        String endpoint,
        String model,
        boolean available,
        String version,
        boolean installed,
        String reason) {
}
