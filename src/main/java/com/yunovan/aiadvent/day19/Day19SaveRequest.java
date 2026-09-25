package com.yunovan.aiadvent.day19;

public record Day19SaveRequest(
        String query,
        String format,
        String fileName) {
}