package com.yunovan.aiadvent.day18;

import java.util.List;

public record Day18Summary(
        String feed,
        String since,
        long count,
        String firstAt,
        String lastAt,
        Double avgValue,
        Double minValue,
        Double maxValue,
        Double successRate,
        String lastPayload,
        List<Day18Sample> recent) {
}