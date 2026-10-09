package com.yunovan.aiadvent.day28;

import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.List;

public record Day28AnswerResponse(
        String question,
        String engine,
        String model,
        String matchedQuery,
        boolean rewritten,
        int candidatesBefore,
        int filteredOut,
        List<Day21SearchHit> retrievedHits,
        List<String> sources,
        String answer,
        boolean fallback,
        Double groundingPercent,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond,
        String unavailableReason) {

    public Day28AnswerResponse {
        question = question == null ? "" : question;
        engine = engine == null ? "" : engine;
        model = model == null ? "" : model;
        matchedQuery = matchedQuery == null ? "" : matchedQuery;
        retrievedHits = retrievedHits == null ? List.of() : retrievedHits;
        sources = sources == null ? List.of() : sources;
        answer = answer == null ? "" : answer;
        unavailableReason = unavailableReason == null ? "" : unavailableReason;
    }
}
