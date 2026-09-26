package com.yunovan.aiadvent.day20;

import java.util.List;
import java.util.Map;

public record Day20FlowResponse(
        String flow,
        String description,
        Map<String, Object> arguments,
        List<Day20FlowStepResult> steps,
        String summary) {
}