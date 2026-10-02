package com.yunovan.aiadvent.day25;

import java.util.List;

public record Day25EvalResponse(
        int scenariosCount,
        int totalTurns,
        int turnsWithSources,
        int quotesTurns,
        int supportedTurns,
        int goalRetainedTurns,
        int constraintsKeptTurns,
        int unknownTurns,
        double avgSupportPercent,
        String verdict,
        List<Day25ScenarioResult> scenarios) {
}
