package com.yunovan.aiadvent.day26;

import java.util.List;

public record Day26Task(
        String id,
        String title,
        String complexity,
        String systemPrompt,
        String prompt) {

    public static final List<Day26Task> ALL = List.of(
            new Day26Task(
                    "simple",
                    "Простой запрос",
                    "simple",
                    "Отвечай по-русски и коротко.",
                    "Привет! Ответь одной фразой: кто ты такой?"),
            new Day26Task(
                    "medium",
                    "Объяснение двух мыслей",
                    "medium",
                    "Отвечай по-русски ровно двумя предложениями, без лишних деталей.",
                    "Чем отличается CPU от GPU при запуске языковых моделей?"),
            new Day26Task(
                    "complex",
                    "Код и рассуждение",
                    "complex",
                    "Отвечай по-русски. Код давай одним блоком ```java, "
                            + "а сложность алгоритма — одной строкой сразу после кода.",
                    "Напиши функцию на Java, которая из списка строк убирает дубликаты, "
                            + "сохраняя порядок, и объясни сложность одной строкой."));

    public Day26Task {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Идентификатор задачи не может быть пустым");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Запрос задачи не может быть пустым");
        }
        systemPrompt = systemPrompt == null ? "" : systemPrompt.trim();
    }
}
