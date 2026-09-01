package com.yunovan.aiadvent.day02;

import java.util.List;

public record CompareResponse(
        String prompt,
        String model,
        CompareSample unconstrained,
        ConstrainedSample constrained) {

    public record ConstrainedSample(
            String content,
            String finishReason,
            int characters,
            int words,
            String formatInstruction,
            int maxTokens,
            List<String> stop) {
    }
}
