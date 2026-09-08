package com.yunovan.aiadvent.agent;

import java.math.BigDecimal;

public record AgentReply(
        String content, String model, Integer promptTokens, Integer completionTokens, Integer totalTokens, BigDecimal costUsd, long elapsedMs) {

    public AgentReply(String content, String model) {
        this(content, model, null, null, null, null, 0L);
    }
}