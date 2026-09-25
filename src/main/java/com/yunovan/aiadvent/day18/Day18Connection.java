package com.yunovan.aiadvent.day18;

public record Day18Connection(
        String protocolVersion,
        String serverName,
        String serverVersion,
        String sessionId) {
}