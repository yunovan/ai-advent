package com.yunovan.aiadvent.day23;

import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.List;

public record Day23Pipeline(
        String question,
        String matchedQuery,
        boolean rewritten,
        int candidatesBefore,
        int filteredOut,
        List<Day21SearchHit> hits) {
}