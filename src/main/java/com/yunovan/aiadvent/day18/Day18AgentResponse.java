package com.yunovan.aiadvent.day18;

import java.util.Map;

public record Day18AgentResponse(
        String prompt,
        String tool,
        Map<String, Object> arguments,
        String toolResult,
        boolean toolError,
        String answer) {
}