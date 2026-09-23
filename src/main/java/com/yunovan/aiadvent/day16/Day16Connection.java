package com.yunovan.aiadvent.day16;

import com.fasterxml.jackson.databind.JsonNode;

public record Day16Connection(
        String protocolVersion,
        String serverName,
        String serverVersion,
        JsonNode capabilities,
        String sessionId) {
}