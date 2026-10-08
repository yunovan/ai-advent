package com.yunovan.aiadvent.day29;

import com.yunovan.aiadvent.day26.Day26LlmException;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day29CliRunner implements ApplicationRunner {

    private final Day29OptimizationService service;
    private final ApplicationContext applicationContext;

    public Day29CliRunner(Day29OptimizationService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"29".equals(firstOption(args, "day"))) {
            return;
        }
        try {
            if (args.containsOption("check")) {
                printHealth(service.health());
                maybeExit(args);
                return;
            }

            if (args.containsOption("ask")) {
                printAsk(service.ask(firstOption(args, "ask")));
                maybeExit(args);
                return;
            }

            if (args.containsOption("run")) {
                printRun(service.run());
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

        System.out.println("Подсказка по веб-интерфейсу: http://localhost:8080/day29.html");
        System.out.println("Состояние: --check | сравнение профилей: --ask=\"<вопрос>\" | "
                + "полный бенчмарк: --run");
        System.out.println("Пример: --day=29 --ask=\"Как устроен поиск?\" --cli");
    }

    void printHealth(Day29HealthResponse health) {
        System.out.println("=== Оптимизация локальной LLM (день 29) ===");
        Day29ModelReport model = health.model();
        System.out.println("Модель: " + model.endpoint() + " / " + model.model());
        if (model.available()) {
            System.out.println("Сервер: да (версия " + model.version() + ") | модель: "
                    + (model.installed() ? "установлена" : "НЕ установлена"));
            if (!model.quantizationLevel().isEmpty()) {
                System.out.println("Квантование: " + model.quantizationLevel()
                        + " | параметры: " + model.parameterSize()
                        + " (" + model.parameterCount() + ")"
                        + " | формат: " + model.format()
                        + " | контекстное окно: " + model.contextLength());
            }
            if (model.modelSizeBytes() > 0) {
                System.out.println("Размер на диске: " + formatMb(model.modelSizeBytes())
                        + " | в памяти сейчас: " + formatMb(model.loadedMemoryBytes()));
            }
            if (!model.reason().isEmpty()) {
                System.out.println("Предупреждение: " + model.reason());
            }
        } else {
            System.out.println("Сервер: нет");
            System.out.println("Причина: " + model.reason());
        }
        printProfile("Базовый профиль", health.baseline());
        printProfile("Оптимизированный профиль", health.tuned());
        System.out.println("Поиск: " + health.retrieval());
        System.out.println("Вопросов в --run: " + health.questionsLimit()
                + " | повторов на вопрос: " + health.benchmarkRuns());
    }

    private static void printProfile(String label, Day29ProfileInfo profile) {
        StringBuilder line = new StringBuilder(label + ": temperature " + profile.temperature()
                + ", max_tokens " + profile.maxTokens());
        if (profile.numCtx() != null) {
            line.append(", num_ctx ").append(profile.numCtx());
        }
        line.append(", шаблон ").append(profile.promptTemplate());
        System.out.println(line);
    }

    void printAsk(Day29AskResponse ask) {
        System.out.println("=== Сравнение профилей ===");
        System.out.println("Вопрос: " + ask.question());
        System.out.println("Источники: " + (ask.sources().isEmpty()
                ? "ничего не найдено" : String.join(", ", ask.sources())));
        System.out.println("Размер промпта: " + ask.promptCharsBaseline() + " символов → "
                + ask.promptCharsTuned() + " символов (−"
                + (ask.promptCharsBaseline() - ask.promptCharsTuned()) + ")");
        printSide("Базовый", ask.baseline());
        printSide("Оптимизированный", ask.tuned());
        System.out.println("Качество: " + ask.qualityVerdict());
        System.out.println("Скорость: " + ask.speedVerdict());
    }

    private static void printSide(String label, Day29AnswerResponse answer) {
        System.out.println("--- " + label + " (" + answer.profileTitle() + ") ---");
        System.out.println("Ответ: " + answer.answer());
        System.out.println("Задержка: " + answer.latencyMs() + " мс | токены "
                + answer.promptTokens() + " вход / " + answer.outputTokens() + " выход | "
                + String.format(Locale.ROOT, "%.1f", answer.tokensPerSecond()) + " ток/с"
                + (answer.groundingPercent() == null ? ""
                : " | опора на контекст "
                        + String.format(Locale.ROOT, "%.1f", answer.groundingPercent()) + "%")
                + (answer.fallback() ? " | фолбэк: " + answer.unavailableReason() : ""));
    }

    void printRun(Day29RunResponse run) {
        System.out.println("=== Бенчмарк профилей ===");
        System.out.println("Вопросов: " + run.questions() + " | повторов на вопрос: " + run.runs());
        printStats("Базовый", run.baseline());
        printStats("Оптимизированный", run.tuned());
        if (run.loadedMemoryBytes() > 0) {
            System.out.println("Память модели после прогона: "
                    + formatMb(run.loadedMemoryBytes()));
        }
        System.out.println("Качество: " + run.qualityVerdict());
        System.out.println("Скорость: " + run.speedVerdict());
        System.out.println("Ресурсы: " + run.resourceVerdict());
    }

    private static void printStats(String label, Day29ProfileStats stats) {
        StringBuilder line = new StringBuilder(label + ": ответов " + stats.answers());
        if (stats.avgCoveragePercent() != null) {
            line.append(" | покрытие ")
                    .append(String.format(Locale.ROOT, "%.1f", stats.avgCoveragePercent())).append('%');
        }
        if (stats.avgGroundingPercent() != null) {
            line.append(" | опора ")
                    .append(String.format(Locale.ROOT, "%.1f", stats.avgGroundingPercent())).append('%');
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
        if (stats.avgInputTokens() != null) {
            line.append(" | токены ")
                    .append(String.format(Locale.ROOT, "%.0f", stats.avgInputTokens()))
                    .append(" вход / ")
                    .append(String.format(Locale.ROOT, "%.0f", stats.avgOutputTokens()))
                    .append(" выход");
        }
        if (stats.fallbacks() > 0) {
            line.append(" | фолбэков: ").append(stats.fallbacks());
        }
        System.out.println(line);
    }

    private static String formatMb(long bytes) {
        return String.format(Locale.ROOT, "%.0f МБ", bytes / 1_048_576.0);
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
