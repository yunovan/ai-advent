package com.yunovan.aiadvent.day31;

import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.List;

public record Day31HelpResponse(
        String question,
        String answer,
        boolean fallback,
        String model,
        String gitBranch,
        boolean gitDirty,
        String gitContext,
        boolean mcpUsed,
        int mcpTools,
        int retrieved,
        List<Day21SearchHit> sources,
        List<String> sourceFiles,
        Double groundingPercent,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond,
        String note) {
}
