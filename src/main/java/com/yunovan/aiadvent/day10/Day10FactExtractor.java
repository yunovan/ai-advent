package com.yunovan.aiadvent.day10;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Day10FactExtractor {

    private static final String SYSTEM_PROMPT =
            "Ты извлекаешь важные факты из сообщения пользователя для блока памяти агента «факты». "
                    + "Каждый факт — это пара «ключ: значение» (например: Цель: собрать ТЗ, Ограничение: бюджет 10 000$, "
                    + "Предпочтение: тёмная тема). Выведи каждый факт отдельной строкой в формате «Ключ: значение». "
                    + "Если новых фактов нет — выведи одну пустую строку.";

    private final LlmClient llmClient;
    private final LlmProperties properties;

    public Day10FactExtractor(LlmClient llmClient, LlmProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public List<Day10Fact> extract(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return List.of();
        }
        if (properties.hasApiKey()) {
            try {
                String answer = llmClient.complete(CompletionCommand
                                .unconstrained(userMessage.trim())
                                .withSystemPrompt(SYSTEM_PROMPT))
                        .content();
                List<Day10Fact> facts = parse(answer);
                if (!facts.isEmpty()) {
                    return facts;
                }
            } catch (LlmException ex) {
                // fall through to local extraction
            }
        }
        return parse(userMessage);
    }

    private static List<Day10Fact> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Day10Fact> facts = new ArrayList<>();
        for (String line : text.split("\n")) {
            int separator = line.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = line.substring(0, separator).trim();
            String value = line.substring(separator + 1).trim();
            if (!key.isBlank() && !value.isBlank()) {
                facts.add(new Day10Fact(key, value, true));
            }
        }
        return facts;
    }
}