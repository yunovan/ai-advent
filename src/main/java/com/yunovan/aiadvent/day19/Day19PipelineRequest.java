package com.yunovan.aiadvent.day19;

public record Day19PipelineRequest(
        String query,
        String format,
        String fileName) {
}