package com.yunovan.aiadvent.day27;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day27")
public record Day27Properties(
        String systemPrompt,
        Integer historyLimit,
        Integer maxSessions,
        String storeDir) {

    public static final String DEFAULT_SYSTEM_PROMPT = "Ты — «Локальный ассистент» проекта AI Advent. "
            + "Работаешь на локальной языковой модели без доступа к интернету. Отвечай по-русски, "
            + "коротко и по делу, помни контекст текущего диалога и учитывай предыдущие сообщения. "
            + "Если вопрос требует данных из сети или облачных сервисов — честно скажи об этом.";
    public static final int DEFAULT_HISTORY_LIMIT = 40;
    public static final int DEFAULT_MAX_SESSIONS = 50;
    public static final String DEFAULT_STORE_DIR = "data/day27-assistant";

    public Day27Properties {
        systemPrompt = valueOr(systemPrompt, DEFAULT_SYSTEM_PROMPT);
        historyLimit = clamp(historyLimit, 2, 500, DEFAULT_HISTORY_LIMIT);
        maxSessions = clamp(maxSessions, 1, 5_000, DEFAULT_MAX_SESSIONS);
        storeDir = valueOr(storeDir, DEFAULT_STORE_DIR);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int clamp(Integer value, int min, int max, int fallback) {
        if (value == null || value <= 0) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
