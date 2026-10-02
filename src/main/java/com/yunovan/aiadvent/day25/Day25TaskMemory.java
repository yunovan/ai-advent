package com.yunovan.aiadvent.day25;

import java.util.ArrayList;
import java.util.List;

public record Day25TaskMemory(
        String goal,
        int turn,
        int userTurns,
        List<String> clarifications,
        List<String> constraints,
        List<String> terms) {

    public Day25TaskMemory withGoal(String value, int turn) {
        return new Day25TaskMemory(value, turn, userTurns, clarifications, constraints, terms);
    }

    public Day25TaskMemory withUserTurn(int turn) {
        return new Day25TaskMemory(goal, this.turn, userTurns, clarifications, constraints, terms);
    }

    public Day25TaskMemory withClarification(String value, int limit) {
        List<String> merged = appendLimited(clarifications, value, limit);
        return new Day25TaskMemory(goal, this.turn, userTurns, merged, constraints, terms);
    }

    public Day25TaskMemory withConstraint(String value, int limit) {
        List<String> merged = appendLimited(constraints, value, limit);
        return new Day25TaskMemory(goal, this.turn, userTurns, clarifications, merged, terms);
    }

    public Day25TaskMemory withTerms(List<String> values, int limit) {
        List<String> merged = new ArrayList<>(terms);
        for (String value : values) {
            if (!merged.contains(value)) {
                merged.add(value);
            }
        }
        List<String> result = merged.size() > limit ? new ArrayList<>(merged.subList(0, limit))
                : merged;
        return new Day25TaskMemory(goal, this.turn, userTurns, clarifications, constraints, result);
    }

    public Day25TaskMemory advanced(int turn) {
        return new Day25TaskMemory(goal, turn, userTurns + 1, clarifications, constraints, terms);
    }

    private static List<String> appendLimited(List<String> current, String value, int limit) {
        List<String> merged = new ArrayList<>(current == null ? List.of() : current);
        if (value != null && !value.isBlank() && !merged.contains(value)) {
            merged.add(value);
        }
        return merged.size() > limit ? new ArrayList<>(merged.subList(0, limit)) : merged;
    }
}
