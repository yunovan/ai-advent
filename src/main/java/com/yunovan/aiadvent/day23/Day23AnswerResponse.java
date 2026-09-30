package com.yunovan.aiadvent.day23;

import java.util.List;

public record Day23AnswerResponse(
        String question,
        String mode,
        String matchedQuery,
        boolean rewritten,
        int candidatesBefore,
        int filteredOut,
        List<com.yunovan.aiadvent.day21.Day21SearchHit> retrievedHits,
        String answer,
        boolean fallback) {
}