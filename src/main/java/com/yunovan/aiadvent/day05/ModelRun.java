package com.yunovan.aiadvent.day05;

import com.yunovan.aiadvent.llm.LlmReply;
import java.math.BigDecimal;

public record ModelRun(
        String tier,
        String label,
        String model,
        String huggingFaceUrl,
        String openRouterUrl,
        String content,
        String finishReason,
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens,
        BigDecimal costUsd,
        String costLabel,
        long elapsedMs,
        int words) {

    public static ModelRun from(ModelTier tier, String model, LlmReply reply) {
        String text = reply == null || reply.content() == null ? "" : reply.content();
        Integer totalTokens = reply == null ? null : reply.totalTokens();
        BigDecimal costUsd = reply == null ? null : reply.costUsd();
        long elapsedMs = reply == null ? 0L : reply.elapsedMs();
        return new ModelRun(
                tier.id(),
                tier.label(),
                model,
                Day5Models.huggingFaceUrl(model),
                Day5Models.openRouterUrl(model),
                text,
                reply == null ? null : reply.finishReason(),
                reply == null ? null : reply.promptTokens(),
                reply == null ? null : reply.completionTokens(),
                totalTokens,
                costUsd,
                Day5Models.formatCost(costUsd),
                elapsedMs,
                wordCount(text));
    }

    static int wordCount(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }
}
