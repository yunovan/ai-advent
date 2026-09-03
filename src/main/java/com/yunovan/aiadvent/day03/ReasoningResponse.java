package com.yunovan.aiadvent.day03;

public record ReasoningResponse(
        String task,
        String model,
        MethodResult direct,
        MethodResult stepByStep,
        MethodResult metaPrompt,
        MethodResult experts) {
}
