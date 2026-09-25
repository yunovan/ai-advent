package com.yunovan.aiadvent.day19;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(Day19Properties.class)
public class Day19CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day19CliRunner.class);

    private final Day19MarketApi market;
    private final Day19AgentService service;
    private final Day19SaveService saveService;
    private final Day19Properties properties;
    private final ApplicationContext applicationContext;

    public Day19CliRunner(Day19MarketApi market, Day19AgentService service,
                          Day19SaveService saveService, Day19Properties properties,
                          ApplicationContext applicationContext) {
        this.market = market;
        this.service = service;
        this.saveService = saveService;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"19".equals(firstOption(args, "day"))) {
            log.info("Day 19 web UI: http://localhost:8080/day19.html  |  API: /api/day19/health, "
                    + "/api/day19/tools, /api/day19/search, /api/day19/summarize, /api/day19/save, "
                    + "/api/day19/pipeline, /api/day19/agent, /api/day19/files  |  "
                    + "MCP-пайплайн: http://localhost:{}{}", properties.serverPort(), properties.path());
            return;
        }

        try {
            if (args.containsOption("check")) {
                Day19HealthResponse health = service.health();
                System.out.println("=== СОЕДИНЕНИЕ С MCP-ПАЙПЛАЙНОМ ===");
                System.out.println("connected: " + health.connected());
                System.out.println("server: " + health.serverName() + " " + health.serverVersion());
                System.out.println("tools: " + health.toolCount());
                maybeExit(args);
                return;
            }

            if (args.containsOption("tools")) {
                System.out.println("=== ИНСТРУМЕНТЫ MCP ===");
                for (Day19ToolInfo tool : service.tools()) {
                    System.out.println("- " + tool.name() + ": " + tool.description());
                }
                maybeExit(args);
                return;
            }

            String search = firstOption(args, "search");
            if (search != null) {
                List<Day19Product> products = market.search(search,
                        firstOption(args, "category"),
                        optionInt(args, "limit", 0),
                        firstOption(args, "sort"));
                System.out.println("=== ПОИСК: " + search + " ===");
                System.out.println("найдено товаров: " + products.size());
                for (Day19Product product : products) {
                    System.out.println("- " + product.id() + " «" + product.title() + "» ["
                            + product.category() + "] " + product.price() + " " + product.currency()
                            + " (рейтинг " + product.rating() + ") — " + product.seller());
                }
                maybeExit(args);
                return;
            }

            String summarize = firstOption(args, "summarize");
            if (summarize != null) {
                String fmt = firstOption(args, "format");
                String table = market.summarizeQuery(summarize, fmt);
                System.out.println("=== СВОДНАЯ ТАБЛИЦА: " + summarize + " ===");
                System.out.println(table);
                maybeExit(args);
                return;
            }

            String save = firstOption(args, "save");
            if (save != null) {
                Day19SavedFile file = market.saveQuery(save,
                        firstOption(args, "format"), firstOption(args, "file"));
                System.out.println("=== СОХРАНЕНИЕ ФАЙЛА ===");
                System.out.println("файл: " + file.fileName());
                System.out.println("путь: " + file.path());
                System.out.println("формат: " + file.format());
                System.out.println("размер: " + file.bytes() + " байт");
                maybeExit(args);
                return;
            }

            String pipeline = firstOption(args, "pipeline");
            if (pipeline != null) {
                Day19PipelineResponse response = service.pipeline(pipeline,
                        firstOption(args, "format"), firstOption(args, "file"));
                System.out.println("=== ПАЙПЛАЙН: search -> summarize -> saveToFile ===");
                System.out.println("запрос: " + response.query());
                System.out.println("формат: " + response.format());
                for (Day19PipelineStep step : response.steps()) {
                    System.out.println("- " + step.tool() + ": " + (step.success() ? "OK" : "СБОЙ")
                            + " (" + step.note() + ")");
                }
                if (response.saved() != null) {
                    System.out.println("файл: " + response.saved().fileName());
                    System.out.println("путь: " + response.saved().path());
                    System.out.println("размер: " + response.saved().bytes() + " байт");
                }
                maybeExit(args);
                return;
            }

            String prompt = firstOption(args, "prompt");
            if (prompt != null && !prompt.isBlank()) {
                Day19AgentResponse response = service.submit(prompt);
                System.out.println("=== АГЕНТ / MCP ===");
                System.out.println("prompt: " + response.prompt());
                System.out.println("intent: " + (response.intent() == null ? "—" : response.intent()));
                System.out.println("tool: " + (response.tool() == null ? "—" : response.tool()));
                System.out.println("arguments: " + (response.arguments() == null ? "—" : response.arguments()));
                System.out.println("tool result: " + (response.toolResult() == null ? "—" : response.toolResult()));
                System.out.println("answer: ");
                System.out.println(response.answer());
                maybeExit(args);
                return;
            }

            if (args.containsOption("files")) {
                List<Day19SavedFile> files = saveService.list();
                System.out.println("=== СОХРАНЁННЫЕ ФАЙЛЫ ===");
                System.out.println("файлов: " + files.size());
                for (Day19SavedFile file : files) {
                    System.out.println("- " + file.fileName() + " [" + file.format() + "] "
                            + file.bytes() + " байт — " + file.path());
                }
                maybeExit(args);
                return;
            }
        } catch (Day19McpException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        } catch (IllegalArgumentException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        }

        System.out.println("Витрина сравнения товаров доступна на http://localhost:8080/day19.html и "
                + "/api/day19/agent");
        System.out.println("Проверка MCP: --check | Инструменты: --tools");
        System.out.println("Поиск: --search=<запрос> [--category=... --limit=<число> --sort=price_asc|price_desc|rating]");
        System.out.println("Таблица: --summarize=<запрос> [--format=markdown|csv]");
        System.out.println("Файл: --save=<запрос> [--format=markdown|csv|json|txt --file=<имя>]");
        System.out.println("Пайплайн: --pipeline=<запрос> [--format=markdown|csv --file=<имя>]");
        System.out.println("Запрос: --prompt=<текст> | Файлы: --files");
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

    private static int optionInt(ApplicationArguments args, String name, int fallback) {
        String value = firstOption(args, name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}