package com.yunovan.aiadvent.day17;

import java.util.Map;

public record Day17AgentResponse(
        String prompt,
        String tool,
        Map<String, Object> arguments,
        String toolResult,
        boolean toolError,
        String answer) {
}