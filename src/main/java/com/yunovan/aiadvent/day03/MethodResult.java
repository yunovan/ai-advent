package com.yunovan.aiadvent.day03;

public record MethodResult(
        String id,
        String title,
        String sentPrompt,
        String generatedPrompt,
        String content,
        String finishReason) {
}
