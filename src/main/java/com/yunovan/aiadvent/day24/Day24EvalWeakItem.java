package com.yunovan.aiadvent.day24;

import java.util.List;

public record Day24EvalWeakItem(
        String id,
        String question,
        boolean unknown,
        double bestScore,
        List<String> sources) {
}