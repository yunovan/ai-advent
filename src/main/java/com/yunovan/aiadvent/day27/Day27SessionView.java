package com.yunovan.aiadvent.day27;

public record Day27SessionView(
        String sessionId,
        int turns,
        int historySize,
        String lastMessage) {
}
