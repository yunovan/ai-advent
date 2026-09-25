package com.yunovan.aiadvent.day19;

public record Day19Connection(
        String protocolVersion,
        String serverName,
        String serverVersion,
        String sessionId) {
}