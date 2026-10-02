package com.yunovan.aiadvent.day25;

import java.util.List;

public record Day25ScenarioTurn(
        int turn,
        String message,
        boolean hasSources,
        int sourcesCount,
        int quotesCount,
        boolean supported,
        boolean unknown,
        boolean goalRetained,
        boolean constraintsKept,
        double bestScore,
        List<String> sources) {
}
