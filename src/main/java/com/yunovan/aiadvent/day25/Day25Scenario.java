package com.yunovan.aiadvent.day25;

import java.util.List;

public record Day25Scenario(
        String id,
        String title,
        String goal,
        List<String> expectedGoalTerms,
        List<String> expectedConstraints,
        List<String> expectedSources,
        List<String> messages) {
}
