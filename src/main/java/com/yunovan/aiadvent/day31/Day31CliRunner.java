package com.yunovan.aiadvent.day31;

import java.util.Locale;
import java.util.Map;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day31CliRunner implements ApplicationRunner {

    private final Day31DeveloperAssistantService service;
    private final ApplicationContext applicationContext;

    public Day31CliRunner(Day31DeveloperAssistantService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"31".equals(firstOption(args, "day"))) {
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

            String question = firstOption(args, "help");
            if (question == null) {
                question = firstOption(args, "ask");
            }
            if (question != null) {
                printHelp(service.help(question));
                maybeExit(args);
                return;
            }

            String gitTool = firstOption(args, "git");
            if (gitTool != null) {
                printGit(service.git(gitTool, Map.of()));
                maybeExit(args);
                return;
            }
        } catch (IllegalArgumentException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        } catch (Day31GitException | Day31McpException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        }

        System.out.println("Подсказка по веб-интерфейсу: http://localhost:8080/day31.html");
        System.out.println("Возможности: --check | --tools | "
                + "--help=\"<вопрос>\" | --git=branch|status|files|diff|log");
        System.out.println("Пример: --day=31 --help=\"Как устроен RAG в проекте?\" --cli");
    }

    void printHealth(Day31HealthResponse health) {
        System.out.println("=== Ассистент разработчика (день 31) ===");
        System.out.println("Статус: " + health.status());
        System.out.println("MCP-сервер: " + health.endpoint() + " ("
                + (health.mcp().available() ? "доступен, инструментов " + health.mcp().tools()
                        : "нет — " + health.mcp().reason()) + ")");
        System.out.println("Репозиторий: " + health.repositoryDir());
        System.out.println("Git: ветка " + health.branch()
                + (health.dirty() ? " (есть незакоммиченные изменения)" : " (чисто)"));
        System.out.println("Документация: " + health.documents().size() + " файлов, "
                + health.chunks() + " чанков, " + health.corpusChars() + " символов");
        if (!health.documents().isEmpty()) {
            System.out.println("Файлы: " + String.join(", ", health.documents()));
        }
        Day31ModelStatus model = health.model();
        System.out.println("Локальная LLM: " + model.endpoint() + " / " + model.model());
        if (model.available()) {
            System.out.println("Ollama: да (версия " + model.version() + ") | модель: "
                    + (model.installed() ? "установлена" : "НЕ установлена"));
        } else {
            System.out.println("Ollama: нет — " + model.reason());
        }
    }

    void printTools(Day31ToolsResponse tools) {
        System.out.println("=== MCP-инструменты проекта (день 31) ===");
        if (!tools.available()) {
            System.out.println("MCP недоступен: " + tools.reason());
            return;
        }
        System.out.println("Эндпоинт: " + tools.endpoint() + " | инструментов: " + tools.count());
        for (Day31ToolInfo tool : tools.tools()) {
            System.out.println("- " + tool.name() + ": " + tool.description());
        }
    }

    void printGit(Day31GitResponse git) {
        System.out.println("=== Инструмент MCP: " + git.tool() + " ===");
        System.out.println(git.content());
    }

    void printHelp(Day31HelpResponse help) {
        System.out.println("=== Ответ ассистента разработчика ===");
        System.out.println("Вопрос: " + help.question());
        System.out.println("Git-ветка: " + (help.gitBranch().isEmpty() ? "н/д" : help.gitBranch())
                + (help.gitDirty() ? " (есть изменения)" : ""));
        System.out.println("MCP: " + (help.mcpUsed()
                ? "использован, инструментов " + help.mcpTools() : "не использован"));
        System.out.println("Найдено фрагментов: " + help.retrieved()
                + (help.sourceFiles().isEmpty() ? "" : " — источники: "
                        + String.join(", ", help.sourceFiles())));
        System.out.println("Ответ: " + help.answer());
        StringBuilder metrics = new StringBuilder("Задержка: " + help.latencyMs() + " мс"
                + " | токены " + help.promptTokens() + " вход / " + help.outputTokens() + " выход"
                + " | " + String.format(Locale.ROOT, "%.1f", help.tokensPerSecond()) + " ток/с");
        if (help.groundingPercent() != null) {
            metrics.append(" | опора на источник ")
                    .append(String.format(Locale.ROOT, "%.0f", help.groundingPercent())).append('%');
        }
        if (help.fallback()) {
            metrics.append(" | ответ собран из документации (LLM недоступна)");
        }
        System.out.println(metrics);
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
