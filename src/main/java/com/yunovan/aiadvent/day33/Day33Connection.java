package com.yunovan.aiadvent.day33;

public record Day33Connection(
        String protocolVersion,
        String serverName,
        String serverVersion,
        String sessionId) {
}
