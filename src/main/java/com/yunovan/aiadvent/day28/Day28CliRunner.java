package com.yunovan.aiadvent.day28;

import com.yunovan.aiadvent.day26.Day26LlmException;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day28CliRunner implements ApplicationRunner {

    private final Day28RagService service;
    private final ApplicationContext applicationContext;

    public Day28CliRunner(Day28RagService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"28".equals(firstOption(args, "day"))) {
            return;
        }
        try {
            if (args.containsOption("check")) {
                printHealth(service.health());
                maybeExit(args);
                return;
            }

            if (args.containsOption("ask")) {
                printAnswer(service.ask(firstOption(args, "ask")));
                maybeExit(args);
                return;
            }

            if (args.containsOption("compare")) {
                printCompare(service.compare(firstOption(args, "compare")));
                maybeExit(args);
                return;
            }

            if (args.containsOption("evaluate")) {
                printEval(service.evaluate());
                maybeExit(args);
                return;
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        } catch (Day26LlmException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        }

        System.out.println("Подсказка по веб-интерфейсу: http://localhost:8080/day28.html");
        System.out.println("Состояние: --check | локальный ответ: --ask=\"<вопрос>\" | "
                + "сравнение с облаком: --compare=\"<вопрос>\"");
        System.out.println("Полная оценка: --evaluate");
        System.out.println("Пример: --day=28 --ask=\"Как устроен RAG?\" --cli");
    }

    void printHealth(Day28HealthResponse health) {
        System.out.println("=== Локальная RAG-система (день 28) ===");
        System.out.println("Индекс: " + health.documents() + " документов | "
                + health.corpusChars() + " символов | ~" + health.pagesEstimate() + " страниц");
        System.out.println("Поиск: " + health.retrieval());
        System.out.println("Стратегия: " + health.strategy() + " (top-k "
                + health.topKBefore() + "→" + health.topKAfter() + ", порог "
                + String.format(Locale.ROOT, "%.2f", health.threshold()) + ", rewrite: "
                + (health.rewrite() ? "да" : "нет") + ", макс. токенов: "
                + health.answerMaxTokens() + ")");
        System.out.println("Локальная LLM: " + health.localEndpoint() + " / " + health.localModel());
        if (health.localAvailable()) {
            System.out.println("Сервер запущен: да (версия " + health.localVersion()
                    + ") | модель установлена: " + (health.modelInstalled() ? "да" : "нет"));
        } else {
            System.out.println("Сервер запущен: нет");
            System.out.println("Причина: " + health.localError());
        }
        if (health.cloudConfigured()) {
            System.out.println("Облачное сравнение: ключ задан, модель " + health.cloudModel());
        } else {
            System.out.println("Облачное сравнение: ключ не задан — --compare покажет "
                    + "только локальный ответ");
        }
        System.out.println("Облачная генерация в --ask: нет (только локальная модель)");
        System.out.println("Повторов в --evaluate: " + health.evaluateRuns());
    }

    void printAnswer(Day28AnswerResponse answer) {
        System.out.println("=== Локальный ответ (" + answer.model() + ") ===");
        System.out.println("Вопрос: " + answer.question());
        System.out.println("Поисковый запрос: " + answer.matchedQuery());
        System.out.println("Источники: " + (answer.sources().isEmpty()
                ? "ничего не найдено" : String.join(", ", answer.sources())));
        System.out.println("Ответ: " + answer.answer());
        System.out.println("Метрики: задержка " + answer.latencyMs() + " мс | токены "
                + answer.promptTokens() + " вход / " + answer.outputTokens() + " выход | "
                + String.format(Locale.ROOT, "%.1f", answer.tokensPerSecond()) + " ток/с"
                + (answer.groundingPercent() == null ? ""
                : " | опора на контекст "
                        + String.format(Locale.ROOT, "%.1f", answer.groundingPercent()) + "%"));
        if (answer.fallback()) {
            System.out.println("Фолбэк: да — локальная LLM не ответила: "
                    + answer.unavailableReason());
        }
    }

    void printCompare(Day28CompareResponse compare) {
        System.out.println("=== Сравнение: локальная против облачной ===");
        System.out.println("Вопрос: " + compare.question());
        System.out.println("Общий контекст: " + (compare.sources().isEmpty()
                ? "по запросу ничего не найдено"
                : String.join(", ", compare.sources())));
        printSide("Локальная", compare.local());
        printSide("Облачная", compare.cloud());
        System.out.println("Вердикт: " + compare.verdict());
    }

    private static void printSide(String label, Day28AnswerResponse answer) {
        System.out.println("--- " + label + " (" + answer.model() + ") ---");
        if (!answer.unavailableReason().isEmpty()) {
            System.out.println("Недоступна: " + answer.unavailableReason());
            return;
        }
        System.out.println("Ответ: " + answer.answer());
        System.out.println("Задержка: " + answer.latencyMs() + " мс | токены "
                + answer.promptTokens() + " вход / " + answer.outputTokens() + " выход | "
                + String.format(Locale.ROOT, "%.1f", answer.tokensPerSecond()) + " ток/с"
                + (answer.groundingPercent() == null ? ""
                : " | опора на контекст "
                        + String.format(Locale.ROOT, "%.1f", answer.groundingPercent()) + "%")
                + (answer.fallback() ? " | фолбэк" : ""));
    }

    void printEval(Day28EvalResponse eval) {
        System.out.println("=== Оценка локальной RAG-системы ===");
        System.out.println("Вопросов: " + eval.total() + " | повторов на вопрос: " + eval.runs());
        System.out.println("Поиск: ожидаемый источник найден в " + eval.retrievalHits()
                + " из " + eval.total() + " вопросов ("
                + String.format(Locale.ROOT, "%.1f", eval.retrievalRecallPercent()) + "%)");
        printStats("Локальная модель", eval.local());
        printStats("Облачная модель", eval.cloud());
        System.out.println("Качество: " + eval.qualityVerdict());
        System.out.println("Скорость: " + eval.speedVerdict());
        System.out.println("Стабильность: " + eval.stabilityVerdict());
        System.out.println("--- По вопросам ---");
        for (Day28EvalItem item : eval.items()) {
            System.out.println("  " + item.id() + " | ретрив: "
                    + (item.retrievalHit() ? "да" : "НЕТ") + " | лок: "
                    + coverage(item.localCoverages()) + " | обл: "
                    + coverage(item.cloudCoverages()));
        }
    }

    private static void printStats(String label, Day28EngineStats stats) {
        if (!stats.available()) {
            System.out.println(label + ": недоступна — " + stats.reason());
            return;
        }
        StringBuilder line = new StringBuilder(label + ": ");
        line.append("ответов ").append(stats.answered());
        if (stats.avgCoveragePercent() != null) {
            line.append(" | покрытие ")
                    .append(String.format(Locale.ROOT, "%.1f", stats.avgCoveragePercent())).append('%');
        }
        if (stats.coverageStdDev() != null) {
            line.append(" | отклонение ±")
                    .append(String.format(Locale.ROOT, "%.1f", stats.coverageStdDev())).append(" п.п.");
        }
        if (stats.avgLatencyMs() != null) {
            line.append(" | задержка ")
                    .append(String.format(Locale.ROOT, "%.0f", stats.avgLatencyMs())).append(" мс");
        }
        if (stats.avgTokensPerSecond() != null) {
            line.append(" | ")
                    .append(String.format(Locale.ROOT, "%.1f", stats.avgTokensPerSecond()))
                    .append(" ток/с");
        }
        if (stats.fallbacks() > 0) {
            line.append(" | фолбэков: ").append(stats.fallbacks());
        }
        if (!stats.reason().isEmpty()) {
            line.append(" | ").append(stats.reason());
        }
        System.out.println(line);
    }

    private static String coverage(List<Double> values) {
        if (values.isEmpty()) {
            return "—";
        }
        double sum = 0;
        for (Double value : values) {
            sum += value;
        }
        return String.format(Locale.ROOT, "%.1f%%", sum / values.size());
    }

    private void maybeExit(ApplicationArguments args) {
        if (args.containsOption("cli")) {
            int code = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(code);
        }
    }

    private static String firstOption(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }
}
