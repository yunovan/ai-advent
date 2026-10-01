package com.yunovan.aiadvent.day21;

import java.util.Map;

public record Day21AgentResponse(
        String prompt,
        String intent,
        String tool,
        Map<String, Object> arguments,
        String toolResult,
        String answer) {
}