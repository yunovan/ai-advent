package com.yunovan.aiadvent.day34;

import java.util.List;

public record Day34RunResponse(
        String task,
        String taskLabel,
        String goal,
        String target,
        List<Day34PlanStep> plan,
        List<Day34StepResult> steps,
        List<String> evidence,
        int filesTouched,
        List<String> files,
        int matches,
        List<Day34Finding> findings,
        List<Day34Artifact> artifacts,
        String diff,
        List<String> written,
        String summary,
        String markdown,
        boolean fallback,
        String model,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond,
        String note) {
}
