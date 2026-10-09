package com.yunovan.aiadvent.day30;

public record Day30Limits(
        int maxMessages,
        int maxPromptChars,
        int rateLimitPerMinute,
        int maxConcurrent,
        int maxOutputTokens,
        long modelContextLength,
        boolean apiKeyRequired) {
}
