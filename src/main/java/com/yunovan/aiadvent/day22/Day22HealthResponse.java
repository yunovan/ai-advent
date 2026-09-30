package com.yunovan.aiadvent.day22;

import com.yunovan.aiadvent.day21.Day21StrategyInfo;
import java.util.List;

public record Day22HealthResponse(
        int documents,
        int corpusChars,
        int pagesEstimate,
        String strategy,
        int topK,
        int answerMaxTokens,
        List<Day21StrategyInfo> strategies) {
}