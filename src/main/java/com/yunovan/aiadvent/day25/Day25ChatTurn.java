package com.yunovan.aiadvent.day25;

import java.util.List;

public record Day25ChatTurn(
        String sessionId,
        int turn,
        String userMessage,
        String reply,
        String matchedQuery,
        boolean rewritten,
        int candidatesBefore,
        int filteredOut,
        double bestScore,
        List<com.yunovan.aiadvent.day24.Day24Source> sources,
        List<com.yunovan.aiadvent.day24.Day24Quote> quotes,
        double supportCoveragePercent,
        boolean supported,
        boolean unknown,
        boolean fallback,
        Day25TaskMemory memory,
        List<Day25Message> history,
        int historySize) {
}
