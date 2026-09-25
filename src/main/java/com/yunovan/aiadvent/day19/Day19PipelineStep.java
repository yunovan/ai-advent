package com.yunovan.aiadvent.day19;

public record Day19PipelineStep(
        String tool,
        boolean success,
        String note) {
}