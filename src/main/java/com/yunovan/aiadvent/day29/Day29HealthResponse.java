package com.yunovan.aiadvent.day29;

public record Day29HealthResponse(
        Day29ModelReport model,
        Day29ProfileInfo baseline,
        Day29ProfileInfo tuned,
        int benchmarkRuns,
        int questionsLimit,
        String retrieval) {
}
