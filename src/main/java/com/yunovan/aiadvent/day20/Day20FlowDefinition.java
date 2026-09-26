package com.yunovan.aiadvent.day20;

import java.util.List;
import java.util.Map;

public record Day20FlowDefinition(
        String key,
        String description,
        List<Day20FlowStepDefinition> steps) {

    public record Day20FlowStepDefinition(
            String server,
            String tool,
            Map<String, String> arguments) {
    }
}