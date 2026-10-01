package com.yunovan.aiadvent.day21;

import java.util.List;

public record Day21SearchResponse(
        String strategy,
        String query,
        int k,
        List<Day21SearchHit> hits) {
}