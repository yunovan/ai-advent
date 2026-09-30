package com.yunovan.aiadvent.day23;

import com.yunovan.aiadvent.day21.Day21StrategyInfo;
import java.util.List;

public record Day23HealthResponse(
        int documents,
        int corpusChars,
        int pagesEstimate,
        String strategy,
        int topKBefore,
        int topKAfter,
        double threshold,
        boolean rewrite,
        int answerMaxTokens,
        List<Day21StrategyInfo> strategies) {
}