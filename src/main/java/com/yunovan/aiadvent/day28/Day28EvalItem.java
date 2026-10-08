package com.yunovan.aiadvent.day28;

import java.util.List;

public record Day28EvalItem(
        String id,
        String question,
        List<String> expectedKeywords,
        List<String> expectedSources,
        List<String> retrievedSources,
        boolean retrievalHit,
        List<Double> localCoverages,
        List<Long> localLatencies,
        List<Double> cloudCoverages,
        List<Long> cloudLatencies) {

    public Day28EvalItem {
        expectedKeywords = expectedKeywords == null ? List.of() : expectedKeywords;
        expectedSources = expectedSources == null ? List.of() : expectedSources;
        retrievedSources = retrievedSources == null ? List.of() : retrievedSources;
        localCoverages = localCoverages == null ? List.of() : localCoverages;
        localLatencies = localLatencies == null ? List.of() : localLatencies;
        cloudCoverages = cloudCoverages == null ? List.of() : cloudCoverages;
        cloudLatencies = cloudLatencies == null ? List.of() : cloudLatencies;
    }
}
