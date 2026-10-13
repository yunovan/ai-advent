package com.yunovan.aiadvent.day31;

public record Day31Connection(
        String protocolVersion,
        String serverName,
        String serverVersion,
        String sessionId) {
}
