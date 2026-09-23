package com.yunovan.aiadvent.day16;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.function.Function;

public record Day16Tool(
        String name,
        String description,
        JsonNode inputSchema,
        Function<Map<String, Object>, String> handler) {
}