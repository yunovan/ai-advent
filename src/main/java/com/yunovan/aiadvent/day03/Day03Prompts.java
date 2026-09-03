package com.yunovan.aiadvent.day03;

public final class Day03Prompts {

    public static final String DEFAULT_TASK =
            "Бита и мяч вместе стоят 110 рублей. Бита стоит на 100 рублей дороже мяча. Сколько стоит мяч?";

    private Day03Prompts() {
    }

    public static String stepByStep(String task) {
        return task + "\n\nРешай пошагово.";
    }

    public static String metaPromptRequest(String task) {
        return """
                Составь промпт, который поможет языковой модели точно решить задачу ниже.
                Выведи только текст готового промпта, без пояснений и кавычек.

                Задача:
                %s
                """.formatted(task)
                .trim();
    }

    public static String applyGeneratedPrompt(String generatedPrompt, String task) {
        return generatedPrompt.trim() + "\n\nЗадача:\n" + task;
    }

    public static String experts(String task) {
        return """
                Решите задачу как группа из трёх экспертов. Каждый пишет своё решение отдельно.

                Аналитик: разложи условие на уравнения.
                Инженер: проверь арифметику и крайние случаи.
                Критик: найди типичные ошибки и дай итоговый ответ.

                Формат:
                ### Аналитик
                ...
                ### Инженер
                ...
                ### Критик
                ...

                Задача:
                %s
                """.formatted(task)
                .trim();
    }
}
