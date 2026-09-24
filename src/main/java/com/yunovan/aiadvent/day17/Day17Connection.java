package com.yunovan.aiadvent.day17;

public record Day17Connection(
        String protocolVersion,
        String serverName,
        String serverVersion,
        String sessionId) {
}