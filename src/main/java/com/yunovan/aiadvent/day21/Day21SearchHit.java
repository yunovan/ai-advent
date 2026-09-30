package com.yunovan.aiadvent.day21;

public record Day21SearchHit(
        Day21Chunk chunk,
        double score,
        String snippet) {
}