package com.yunovan.aiadvent.day19;

import java.util.List;

public record Day19PipelineResponse(
        String query,
        String format,
        List<Day19PipelineStep> steps,
        Day19SavedFile saved) {
}