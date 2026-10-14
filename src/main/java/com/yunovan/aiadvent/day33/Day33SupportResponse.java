package com.yunovan.aiadvent.day33;

import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.List;

public record Day33SupportResponse(
        String question,
        String ticketId,
        String userId,
        String userName,
        String ticketSubject,
        String answer,
        boolean fallback,
        String model,
        String userContext,
        String ticketContext,
        boolean mcpUsed,
        int mcpTools,
        int retrieved,
        List<Day21SearchHit> sources,
        List<String> sourceFiles,
        Double groundingPercent,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond,
        String note) {
}
