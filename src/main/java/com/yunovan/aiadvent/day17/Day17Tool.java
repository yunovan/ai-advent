package com.yunovan.aiadvent.day17;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.function.Function;

public record Day17Tool(
        String name,
        String description,
        JsonNode inputSchema,
        Function<Map<String, Object>, String> handler) {
}