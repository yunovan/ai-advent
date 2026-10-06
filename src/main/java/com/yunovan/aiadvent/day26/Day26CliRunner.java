package com.yunovan.aiadvent.day26;

import java.util.List;
import java.util.Locale;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day26CliRunner implements ApplicationRunner {

    private final Day26Service service;
    private final ApplicationContext applicationContext;

    public Day26CliRunner(Day26Service service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"26".equals(firstOption(args, "day"))) {
            return;
        }
        try {
            if (args.containsOption("check")) {
                printCheck(service.health());
                maybeExit(args);
                return;
            }

            if (args.containsOption("tasks")) {
                printTasks(service.tasks());
                maybeExit(args);
                return;
            }

            if (args.containsOption("run")) {
                printReport(service.run());
                maybeExit(args);
                return;
            }

            if (args.containsOption("ask")) {
                printAnswer(service.ask(firstOption(args, "ask")));
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

        System.out.println("Локальная LLM: http://localhost:8080/day26.html");
        System.out.println("Состояние: --check | Три запроса: --run | Запрос: --ask=\"<текст>\"");
        System.out.println("Список запросов: --tasks");
        System.out.println("Пример: --day=26 --run --cli");
    }

    void printCheck(Day26HealthResponse health) {
        System.out.println("=== ЛОКАЛЬНАЯ LLM ===");
        System.out.println("endpoint: " + health.endpoint());
        System.out.println("модель: " + health.model());
        if (health.available()) {
            System.out.println("сервер запущен: ДА (версия " + health.version() + ")");
            System.out.println("модель установлена: " + (health.modelInstalled() ? "ДА" : "НЕТ"));
            System.out.println("установлено моделей: " + health.installedModels().size());
            for (Day26InstalledModel model : health.installedModels()) {
                System.out.println("  - " + model.name() + " | " + megabytes(model.sizeBytes()) + " МБ"
                        + (model.parameterSize().isBlank() ? "" : " | " + model.parameterSize())
                        + (model.quantizationLevel().isBlank() ? "" : " | " + model.quantizationLevel()));
            }
        } else {
            System.out.println("сервер запущен: НЕТ");
            System.out.println("причина: " + health.error());
        }
    }

    void printTasks(List<Day26Task> tasks) {
        System.out.println("=== ЗАПРОСЫ РАЗНОЙ СЛОЖНОСТИ (" + tasks.size() + ") ===");
        for (Day26Task task : tasks) {
            System.out.println("[" + task.id() + "] " + task.title() + " (" + task.complexity() + ")");
            System.out.println("  system: " + task.systemPrompt());
            System.out.println("  user:   " + task.prompt());
        }
    }

    void printReport(Day26RunReport report) {
        System.out.println("=== ТРИ ЗАПРОСА К ЛОКАЛЬНОЙ LLM ===");
        System.out.println("endpoint: " + report.endpoint() + " | модель: " + report.model());
        for (Day26TaskResult result : report.results()) {
            System.out.println("[" + result.id() + "] " + result.title() + " ("
                    + result.complexity() + ") — " + (result.ok() ? "ОК" : "ОШИБКА"));
            if (result.ok()) {
                System.out.println("  ответ: " + oneLine(result.reply()));
                System.out.println("  задержка: " + result.latencyMs() + " мс | токены: "
                        + result.promptTokens() + "+ " + result.outputTokens()
                        + " | скорость: " + String.format(Locale.ROOT, "%.1f", result.tokensPerSecond())
                        + " ток/с");
            } else {
                System.out.println("  ошибка: " + result.error());
            }
        }
        System.out.println("итого: " + report.okCount() + "/" + report.total() + " | "
                + report.totalLatencyMs() + " мс");
        System.out.println("вердикт: " + report.verdict());
    }

    void printAnswer(Day26Answer answer) {
        System.out.println("модель: " + answer.model() + " (" + answer.endpoint() + ")");
        System.out.println("ответ: " + answer.reply());
        System.out.println("задержка: " + answer.latencyMs() + " мс | токены: "
                + answer.promptTokens() + "+ " + answer.outputTokens()
                + " | скорость: " + String.format(Locale.ROOT, "%.1f", answer.tokensPerSecond())
                + " ток/с");
    }

    private void maybeExit(ApplicationArguments args) {
        if (args.containsOption("cli")) {
            int code = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(code);
        }
    }

    private static long megabytes(long bytes) {
        return Math.max(1, bytes / (1024 * 1024));
    }

    private static String oneLine(String text) {
        String single = text.replaceAll("\\s+", " ").trim();
        return single.length() > 160 ? single.substring(0, 160) + "…" : single;
    }

    private static String firstOption(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }
}
