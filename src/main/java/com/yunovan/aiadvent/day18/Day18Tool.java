package com.yunovan.aiadvent.day18;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.function.Function;

public record Day18Tool(
        String name,
        String description,
        JsonNode inputSchema,
        Function<Map<String, Object>, String> handler) {
}