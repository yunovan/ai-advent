package com.yunovan.aiadvent.day19;

import java.util.Map;

public record Day19AgentResponse(
        String prompt,
        String intent,
        String tool,
        Map<String, Object> arguments,
        String toolResult,
        String answer) {
}