package com.yunovan.aiadvent.day11;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Day11FactExtractor {

    private static final String SYSTEM_PROMPT =
            "Ты извлекаешь факты из сообщения пользователя для памяти агента. "
                    + "Формат: \"ключ: значение\" (по одному на строку). "
                    + "Если новых фактов нет — одна пустая строка.";

    private final LlmClient llmClient;
    private final LlmProperties properties;

    public Day11FactExtractor(LlmClient llmClient, LlmProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public List<Day11MemoryEntry> extract(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        if (properties.hasApiKey()) {
            try {
                String answer = llmClient.complete(CompletionCommand
                                .unconstrained(userMessage.trim())
                                .withSystemPrompt(SYSTEM_PROMPT))
                        .content();
                List<Day11MemoryEntry> entries = parse(answer);
                if (!entries.isEmpty()) {
                    return entries;
                }
            } catch (LlmException ex) {
            }
        }
        return parse(userMessage);
    }

    private static List<Day11MemoryEntry> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Day11MemoryEntry> entries = new ArrayList<>();
        for (String line : text.split("\n")) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.isBlank() && !value.isBlank()) {
                entries.add(new Day11MemoryEntry(
                        key, value, Day11MemoryLayer.SHORT_TERM, "extracted", true, null));
            }
        }
        return entries;
    }
}
