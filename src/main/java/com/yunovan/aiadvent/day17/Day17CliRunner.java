package com.yunovan.aiadvent.day17;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(Day17Properties.class)
public class Day17CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day17CliRunner.class);

    private final Day17AgentService service;
    private final Day17Properties properties;
    private final ApplicationContext applicationContext;

    public Day17CliRunner(Day17AgentService service, Day17Properties properties, ApplicationContext applicationContext) {
        this.service = service;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"17".equals(firstOption(args, "day"))) {
            log.info("Day 17 web UI: http://localhost:8080/day17.html  |  API: GET /api/day17/health, "
                    + "GET /api/day17/tools, POST /api/day17/agent  |  MCP: http://localhost:{}{}",
                    properties.serverPort(), properties.path());
            return;
        }

        try {
            if (args.containsOption("check")) {
                Day17HealthResponse health = service.health();
                System.out.println("=== СОЕДИНЕНИЕ С MCP ===");
                System.out.println("connected: " + health.connected());
                System.out.println("server: " + health.serverName() + " " + health.serverVersion());
                System.out.println("tools: " + health.toolCount());
                maybeExit(args);
                return;
            }

            if (args.containsOption("tools")) {
                System.out.println("=== ИНСТРУМЕНТЫ MCP ===");
                for (Day17ToolInfo tool : service.tools()) {
                    System.out.println("- " + tool.name() + ": " + tool.description());
                }
                maybeExit(args);
                return;
            }

            String prompt = firstOption(args, "prompt");
            if (prompt != null && !prompt.isBlank()) {
                Day17AgentResponse response = service.submit(prompt);
                System.out.println("=== АГЕНТ / MCP ===");
                System.out.println("prompt: " + response.prompt());
                System.out.println("tool: " + (response.tool() == null ? "—" : response.tool()));
                System.out.println("arguments: " + (response.arguments() == null ? "—" : response.arguments()));
                System.out.println("tool result: " + (response.toolResult() == null ? "—" : response.toolResult()));
                System.out.println("answer: ");
                System.out.println(response.answer());
                maybeExit(args);
                return;
            }
        } catch (Day17McpException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        }

        System.out.println("Агент с инструментом MCP доступен на http://localhost:8080/day17.html и /api/day17/agent");
        System.out.println("Проверка: --check | Инструменты: --tools | Запрос: --prompt=<текст>");
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