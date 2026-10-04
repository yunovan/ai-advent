package com.yunovan.aiadvent.day25;

public record Day25SessionView(
        String sessionId,
        int turns,
        int historySize,
        String goal,
        String lastMessage,
        int userTurns,
        int constraints,
        int terms) {
}
