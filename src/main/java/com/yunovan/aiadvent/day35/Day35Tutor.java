package com.yunovan.aiadvent.day35;

import com.yunovan.aiadvent.day26.Day26Answer;
import com.yunovan.aiadvent.day26.Day26ChatMessage;
import com.yunovan.aiadvent.day26.Day26ChatOptions;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class Day35Tutor {

    private static final String SYSTEM_PROMPT =
            "Ты — терпеливый школьный учитель математики. "
            + "Отвечай на русском языке, кратко и понятно для ученика 5–9 класса. "
            + "Всегда объясняй решение по шагам.";

    private final Day26LocalLlmClient llm;
    private final Day35Properties properties;

    public Day35Tutor(Day26LocalLlmClient llm, Day35Properties properties) {
        this.llm = llm;
        this.properties = properties;
    }

    public boolean isCorrect(String userAnswer, String expectedAnswer) {
        return normalize(userAnswer).equals(normalize(expectedAnswer));
    }

    public Day35TutorResult review(Day35Problem problem, String userAnswer, String expectedAnswer) {
        boolean correct = isCorrect(userAnswer, expectedAnswer);
        Day35Solution solution = explain(problem, userAnswer, expectedAnswer, correct);
        Day35ErrorAnalysis analysis = analyze(problem, userAnswer, expectedAnswer, correct);
        return new Day35TutorResult(
                solution, analysis,
                solution.model(), solution.latencyMs(),
                solution.promptTokens(), solution.outputTokens(), solution.tokensPerSecond());
    }

    private Day35Solution explain(Day35Problem problem, String userAnswer, String expectedAnswer, boolean correct) {
        String prompt = """
                Задача: %s
                Правильный ответ: %s
                Ответ ученика: %s
                Ответ верный: %s

                Объясни пошагово решение этой задачи (3–5 шагов).
                Если ответ ученика неверный — укажи вероятную причину ошибки и как её исправить.
                """.formatted(problem.question(), expectedAnswer, dash(userAnswer), correct ? "да" : "нет");
        try {
            List<Day26ChatMessage> messages = List.of(
                    new Day26ChatMessage("system", SYSTEM_PROMPT),
                    new Day26ChatMessage("user", prompt));
            Day26Answer answer = llm.chat(messages,
                    Day26ChatOptions.defaults(properties.temperature(), properties.answerMaxTokens()));
            String steps = defaultSteps(problem, expectedAnswer);
            return new Day35Solution(problem.id(), expectedAnswer, answer.reply(), steps,
                    correct ? "correct" : "incorrect",
                    answer.model(), answer.latencyMs(), answer.promptTokens(),
                    answer.outputTokens(), answer.tokensPerSecond());
        } catch (RuntimeException ex) {
            return new Day35Solution(problem.id(), expectedAnswer,
                    fallbackExplanation(problem, expectedAnswer, correct),
                    defaultSteps(problem, expectedAnswer),
                    correct ? "correct" : "incorrect",
                    "", 0L, 0, 0, 0.0);
        }
    }

    private Day35ErrorAnalysis analyze(Day35Problem problem, String userAnswer, String expectedAnswer, boolean correct) {
        if (correct) {
            return new Day35ErrorAnalysis("Ошибок нет", "", "Отличная работа — переходите к следующей задаче.");
        }
        if (userAnswer == null || userAnswer.isBlank()) {
            return new Day35ErrorAnalysis(
                    "Ответ не введён",
                    "Запишите ход решения и итоговый ответ.",
                    "Начните с внимательного чтения условия и подсказки.");
        }
        return new Day35ErrorAnalysis(
                "Получен ответ «%s», а ожидался «%s».".formatted(userAnswer.trim(), expectedAnswer),
                "Проверьте арифметику и применённое правило.",
                "Разберите решение по шагам из объяснения выше.");
    }

    private static String fallbackExplanation(Day35Problem problem, String expectedAnswer, boolean correct) {
        if (correct) {
            return "Ответ верный. Правильный ответ: " + expectedAnswer + ". "
                    + "Подсказка по теме: " + problem.solutionHint();
        }
        return "Ответ пока неверный. Правильный ответ: " + expectedAnswer + ". "
                + "Как решать: " + problem.solutionHint()
                + " (локальная модель недоступна, показан встроенный разбор).";
    }

    private static String defaultSteps(Day35Problem problem, String expectedAnswer) {
        return String.join("\n",
                "Шаг 1. Внимательно прочитать условие задачи.",
                "Шаг 2. Выделить известные данные и то, что нужно найти.",
                "Шаг 3. Вспомнить подходящее правило: " + problem.solutionHint(),
                "Шаг 4. Выполнить вычисления и получить ответ: " + expectedAnswer,
                "Шаг 5. Проверить результат подстановкой или рассуждением.");
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim().toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace(",", ".")
                .replace("½", "1/2")
                .replace("¼", "1/4")
                .replace("¾", "3/4");
        if (result.startsWith("x=") || result.startsWith("s=") || result.startsWith("=")) {
            result = result.substring(result.indexOf('=') + 1);
        }
        return result;
    }
}
