package com.yunovan.aiadvent.day34;

import java.util.Map;

public record Day34PlanStep(int index, String tool, Map<String, Object> arguments, String purpose) {

    public static Day34PlanStep of(int index, String tool, Map<String, Object> arguments, String purpose) {
        return new Day34PlanStep(index, tool, Map.copyOf(arguments), purpose);
    }
}
