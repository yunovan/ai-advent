package com.yunovan.aiadvent.day16;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(Day16Properties.class)
public class Day16CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day16CliRunner.class);

    private final Day16McpService service;
    private final Day16Properties properties;
    private final ApplicationContext applicationContext;

    public Day16CliRunner(Day16McpService service, Day16Properties properties, ApplicationContext applicationContext) {
        this.service = service;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"16".equals(firstOption(args, "day"))) {
            log.info("Day 16 web UI: http://localhost:8080/day16.html  |  API: GET /api/day16/health, "
                    + "GET /api/day16/tools, POST /api/day16/call  |  MCP: http://localhost:{}{}",
                    properties.serverPort(), properties.path());
            return;
        }

        try {
            if (args.containsOption("check")) {
                Day16HealthResponse health = service.health();
                System.out.println("=== СОЕДИНЕНИЕ С MCP ===");
                System.out.println("connected: " + health.connected());
                System.out.println("protocol: " + health.protocolVersion());
                System.out.println("server: " + health.serverName() + " " + health.serverVersion());
                System.out.println("session: " + health.sessionId());
                System.out.println("tools: " + health.toolCount());
                maybeExit(args);
                return;
            }

            if (args.containsOption("tools")) {
                List<Day16ToolInfo> tools = service.tools();
                System.out.println("=== ИНСТРУМЕНТЫ MCP ===");
                for (Day16ToolInfo tool : tools) {
                    System.out.println("- " + tool.name() + ": " + tool.description());
                }
                maybeExit(args);
                return;
            }

            String toolName = firstOption(args, "call");
            if (toolName != null && !toolName.isBlank()) {
                Day16CallResponse response = service.call(toolName, parseArguments(args));
                System.out.println("=== ВЫЗОВ ИНСТРУМЕНТА " + response.tool() + " ===");
                System.out.println(response.isError() ? "ОШИБКА: " + response.result() : response.result());
                maybeExit(args);
                return;
            }
        } catch (Day16McpException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        }

        System.out.println("MCP-сервер на порту " + properties.serverPort() + properties.path()
                + ", подключение и список инструментов: --check | --tools | --call=<имя> [--arg=k=v ...]");
    }

    private Map<String, Object> parseArguments(ApplicationArguments args) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        var values = args.getOptionValues("arg");
        if (values == null || values.isEmpty()) {
            return arguments;
        }
        for (String pair : values) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String key = pair.substring(0, separator);
            String value = pair.substring(separator + 1);
            if (isInteger(value)) {
                arguments.put(key, Integer.parseInt(value));
            } else {
                arguments.put(key, value);
            }
        }
        return arguments;
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
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