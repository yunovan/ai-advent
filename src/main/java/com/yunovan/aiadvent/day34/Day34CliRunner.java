package com.yunovan.aiadvent.day34;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day34CliRunner implements ApplicationRunner {

    private final Day34FileService service;
    private final ApplicationContext applicationContext;

    public Day34CliRunner(Day34FileService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"34".equals(firstOption(args, "day"))) {
            return;
        }
        try {
            if (args.containsOption("check")) {
                printHealth(service.health());
                maybeExit(args);
                return;
            }
            if (args.containsOption("tools")) {
                printTools(service.tools());
                maybeExit(args);
                return;
            }
            if (args.containsOption("files")) {
                printFiles(service.files(firstOption(args, "prefix"), intOption(args, "limit")));
                maybeExit(args);
                return;
            }
            if (args.containsOption("read")) {
                printContent(service.read(new Day34ReadRequest(
                        firstOption(args, "read"), intOption(args, "from"), intOption(args, "lines"))));
                maybeExit(args);
                return;
            }
            if (args.containsOption("search")) {
                printMatches(service.search(new Day34SearchRequest(
                        firstOption(args, "search"), firstOption(args, "glob"), intOption(args, "limit"))));
                maybeExit(args);
                return;
            }
            String goal = goal(args);
            if (goal != null) {
                Day34RunRequest request = new Day34RunRequest(goal, firstOption(args, "task"),
                        firstOption(args, "target"), args.containsOption("write"), null);
                Day34RunResponse response = service.run(request);
                printRun(response);
                writeOut(args, response);
                maybeExit(args);
                return;
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("Ошибка: " + ex.getMessage());
            maybeExit(args);
            return;
        } catch (Day34McpException ex) {
            System.out.println("Ошибка MCP: " + ex.getMessage());
            maybeExit(args);
            return;
        } catch (Day34Exception ex) {
            System.out.println("Ошибка: " + ex.getMessage());
            maybeExit(args);
            return;
        }
        printUsage();
    }

    private void printUsage() {
        System.out.println("Ассистент по файлам проекта (день 34): http://localhost:8080/day34.html");
        System.out.println("Запуск: --run=\"<цель>\" [--task=<usages|adr|changelog|readme|invariants|update>] "
                + "[--target=<тема>] [--write] [--out=<path>] [--cli]");
        System.out.println("Утилиты: --check | --tools | --files [--prefix=] [--limit=] | "
                + "--read=<path> [--from=] [--lines=] | --search=<regexp> [--glob=] [--limit=]");
        System.out.println("Пример: --day=34 --run=\"Найди использования Day34Workspace\" --cli");
        System.out.println("Цель также можно передать через переменную окружения DAY34_GOAL.");
    }

    void printHealth(Day34HealthResponse health) {
        System.out.println("=== Состояние ассистента файлов (день 34) ===");
        System.out.println("Статус: " + health.status());
        System.out.println("Рабочий каталог: " + health.workspaceDir()
                + (health.workspaceAvailable() ? "" : " (недоступен)"));
        System.out.println("Исходников: " + health.codeFiles() + " | документов: " + health.documents()
                + " | оценка чанков: " + health.chunks() + " | символов: " + health.corpusChars());
        Day34McpStatus mcp = health.mcp();
        if (mcp.available()) {
            System.out.println("MCP: " + mcp.endpoint() + " (" + mcp.tools() + " инструментов)");
        } else {
            System.out.println("MCP: недоступен — " + mcp.reason());
        }
        Day34ModelStatus model = health.model();
        System.out.println("Локальная LLM: " + model.endpoint() + " / " + model.model());
        if (model.available()) {
            System.out.println("Ollama: ок (версия " + model.version() + ") | модель: "
                    + (model.installed() ? "установлена" : "не установлена"));
        } else {
            System.out.println("Ollama: нет — " + model.reason() + " (документы будут без модели)");
        }
    }

    void printTools(List<Day34ToolInfo> tools) {
        System.out.println("=== MCP-инструменты файлового сервера ===");
        for (Day34ToolInfo tool : tools) {
            System.out.println("- " + tool.name() + " — " + tool.description());
        }
    }

    void printFiles(List<Day34FileInfo> files) {
        System.out.println("=== Файлы проекта: " + files.size() + " ===");
        for (Day34FileInfo file : files) {
            System.out.println(file.path() + " (" + file.lines() + " строк, " + file.sizeBytes() + " байт)");
        }
    }

    void printContent(Day34FileContent content) {
        System.out.println("=== " + content.path() + " (строки " + content.startLine() + "-"
                + content.endLine() + " из " + content.totalLines() + ") ===");
        System.out.println(content.content());
        if (content.truncated()) {
            System.out.println("… (вывод обрезан)");
        }
    }

    void printMatches(List<Day34SearchMatch> matches) {
        System.out.println("=== Совпадения: " + matches.size() + " ===");
        for (Day34SearchMatch match : matches) {
            System.out.println(match.path() + ":" + match.line() + ": " + match.text());
        }
    }

    void printRun(Day34RunResponse run) {
        System.out.println("=== Ассистент файлов (день 34) ===");
        System.out.println("Задача: " + run.taskLabel() + " (" + run.task() + ")");
        System.out.println("Цель: " + run.goal());
        if (run.target() != null) {
            System.out.println("Тема/цель: " + run.target());
        }
        System.out.println("План:");
        for (Day34PlanStep step : run.plan()) {
            System.out.println("  " + step.index() + ". " + step.tool() + " " + step.arguments()
                    + " — " + step.purpose());
        }
        System.out.println("Шагов выполнено: " + run.steps().size()
                + " | файлов затронуто: " + run.filesTouched()
                + " | совпадений: " + run.matches()
                + " | замечаний: " + run.findings().size());
        if (!run.files().isEmpty()) {
            System.out.println("Файлы: " + String.join(", ", run.files()));
        }
        System.out.println("Итог: " + run.summary());
        System.out.println("Соединение: " + run.note());
        System.out.println();
        System.out.println(run.markdown());
        if (!run.artifacts().isEmpty()) {
            System.out.println();
            System.out.println("=== Артефакты ===");
            for (Day34Artifact artifact : run.artifacts()) {
                System.out.println("- " + artifact.action() + " " + artifact.path()
                        + " (" + artifact.bytes() + " байт)");
            }
        }
        if (!run.diff().isBlank()) {
            System.out.println();
            System.out.println("=== Diff ===");
            System.out.println(run.diff());
        }
        if (!run.written().isEmpty()) {
            System.out.println("Записано: " + String.join(", ", run.written()));
        }
        if (run.fallback()) {
            System.out.println("(LLM недоступна — результат собран без модели)");
        } else {
            System.out.println("Модель: " + run.model() + " | " + run.latencyMs() + " мс, токенов: "
                    + run.promptTokens() + "+" + run.outputTokens() + " ("
                    + String.format("%.1f", run.tokensPerSecond()) + " ток/с)");
        }
    }

    private void writeOut(ApplicationArguments args, Day34RunResponse response) {
        String out = firstOption(args, "out");
        if (out == null || out.isBlank()) {
            return;
        }
        try {
            Path path = Path.of(out);
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, response.markdown(), StandardCharsets.UTF_8);
            System.out.println("Документ сохранён: " + path.toAbsolutePath());
        } catch (IOException ex) {
            throw new IllegalArgumentException("Не удалось сохранить документ: " + ex.getMessage(), ex);
        }
    }

    private static String goal(ApplicationArguments args) {
        String value = firstOption(args, "run");
        if (value != null && !value.isBlank()) {
            return value;
        }
        String fromEnvironment = System.getenv("DAY34_GOAL");
        if (fromEnvironment != null && !fromEnvironment.isBlank()) {
            return fromEnvironment;
        }
        return null;
    }

    private void maybeExit(ApplicationArguments args) {
        if (args.containsOption("cli")) {
            int code = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(code);
        }
    }

    private static Integer intOption(ApplicationArguments args, String name) {
        String value = firstOption(args, name);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return null;
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
