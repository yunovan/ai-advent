package com.yunovan.aiadvent.day28;

import java.util.List;

public record Day28EvalResponse(
        int total,
        int runs,
        int retrievalHits,
        double retrievalRecallPercent,
        Day28EngineStats local,
        Day28EngineStats cloud,
        String qualityVerdict,
        String speedVerdict,
        String stabilityVerdict,
        List<Day28EvalItem> items) {

    public Day28EvalResponse {
        qualityVerdict = qualityVerdict == null ? "" : qualityVerdict;
        speedVerdict = speedVerdict == null ? "" : speedVerdict;
        stabilityVerdict = stabilityVerdict == null ? "" : stabilityVerdict;
        items = items == null ? List.of() : items;
    }
}
