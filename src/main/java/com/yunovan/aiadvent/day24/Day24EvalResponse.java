package com.yunovan.aiadvent.day24;

import java.util.List;

public record Day24EvalResponse(
        int knownCount,
        int sourcesPresent,
        int quotesPresent,
        int supportedCount,
        double avgSupportPercent,
        int weakCount,
        int unknownTriggered,
        String verdict,
        List<Day24EvalItem> questions,
        List<Day24EvalWeakItem> weak) {
}