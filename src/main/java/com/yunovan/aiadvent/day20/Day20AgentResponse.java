package com.yunovan.aiadvent.day20;

import java.util.Map;

public record Day20AgentResponse(
        String prompt,
        String intent,
        String server,
        String tool,
        Map<String, Object> arguments,
        String toolResult,
        String answer) {
}