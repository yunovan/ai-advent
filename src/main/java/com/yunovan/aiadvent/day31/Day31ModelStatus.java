package com.yunovan.aiadvent.day31;

public record Day31ModelStatus(
        String endpoint,
        String model,
        boolean available,
        String version,
        boolean installed,
        String reason) {
}
