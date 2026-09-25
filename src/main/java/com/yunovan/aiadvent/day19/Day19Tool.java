package com.yunovan.aiadvent.day19;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.function.Function;

public record Day19Tool(
        String name,
        String description,
        JsonNode inputSchema,
        Function<Map<String, Object>, String> handler) {
}