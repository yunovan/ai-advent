package com.yunovan.aiadvent.day25;

import java.util.List;

public record Day25ScenarioResult(
        String id,
        String title,
        String goal,
        int turns,
        int turnsWithSources,
        int quotesTurns,
        int supportedTurns,
        int goalRetainedTurns,
        int constraintsKeptTurns,
        double avgSupportPercent,
        int unknownTurns,
        Day25TaskMemory finalMemory,
        List<Day25ScenarioTurn> details) {
}
