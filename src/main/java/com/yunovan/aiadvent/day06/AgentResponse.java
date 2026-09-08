package com.yunovan.aiadvent.day06;

import java.math.BigDecimal;

public record AgentResponse(
        String request, String content, String model, Integer totalTokens, BigDecimal costUsd, long elapsedMs) {
}