package com.yunovan.aiadvent.day20;

import com.fasterxml.jackson.databind.ObjectMapper;
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
@EnableConfigurationProperties(Day20Properties.class)
public class Day20CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day20CliRunner.class);

    private final Day20Orchestrator orchestrator;
    private final Day20AgentService service;
    private final Day20Properties properties;
    private final ApplicationContext applicationContext;
    private final ObjectMapper mapper = new ObjectMapper();

    public Day20CliRunner(Day20Orchestrator orchestrator, Day20AgentService service,
                          Day20Properties properties, ApplicationContext applicationContext) {
        this.orchestrator = orchestrator;
        this.service = service;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"20".equals(firstOption(args, "day"))) {
            log.info("Day 20 web UI: http://localhost:8080/day20.html  |  API: /api/day20/health, "
                    + "/api/day20/servers, /api/day20/tools, /api/day20/flows, /api/day20/call, "
                    + "/api/day20/route, /api/day20/flow, /api/day20/agent  |  "
                    + "MCP-оркестратор: http://localhost:{}{}",
                    properties.serverPort(), properties.path());
            return;
        }

        try {
            if (args.containsOption("check")) {
                Day20HealthResponse health = orchestrator.health();
                System.out.println("=== ОРКЕСТРАТОР MCP ===");
                System.out.println("соединение: " + (health.connected() ? "ОК" : "частично"));
                System.out.println("всего инструментов: " + health.toolCount());
                for (Day20ServerInfo server : health.servers()) {
                    System.out.println("- " + server.server() + ": " + server.name() + " "
                            + server.version() + " (" + server.toolCount() + " инструментов, "
                            + (server.connected() ? "подключён" : "недоступен") + ")");
                }
                maybeExit(args);
                return;
            }

            if (args.containsOption("servers")) {
                System.out.println("=== ЗАРЕГИСТРИРОВАННЫЕ MCP-СЕРВЕРЫ ===");
                for (Day20ServerInfo server : orchestrator.health().servers()) {
                    System.out.println("- " + server.server() + ": " + server.name() + " "
                            + server.version() + " (" + server.toolCount() + " инструментов, "
                            + (server.connected() ? "подключён" : "недоступен") + ")");
                }
                maybeExit(args);
                return;
            }

            if (args.containsOption("tools")) {
                System.out.println("=== ИНСТРУМЕНТЫ ВСЕХ MCP-СЕРВЕРОВ ===");
                for (Day20ToolEntry tool : orchestrator.tools()) {
                    System.out.println("- [" + tool.server() + "] " + tool.name() + ": "
                            + tool.description());
                }
                maybeExit(args);
                return;
            }

            if (args.containsOption("flows")) {
                System.out.println("=== ДЛИННЫЕ ФЛОУ ===");
                for (Day20FlowDefinition flow : orchestrator.flows()) {
                    System.out.println("- " + flow.key() + ": " + flow.description());
                }
                maybeExit(args);
                return;
            }

            String routeTool = firstOption(args, "route");
            if (routeTool != null) {
                Day20CallResponse response = orchestrator.route(routeTool, parseArgs(args));
                System.out.println("=== МАРШРУТИЗАЦИЯ: " + routeTool + " ===");
                System.out.println("сервер: " + response.server());
                System.out.println("аргументы: " + response.arguments());
                System.out.println("успех: " + response.success());
                System.out.println("результат: ");
                System.out.println(response.result());
                maybeExit(args);
                return;
            }

            String callTool = firstOption(args, "call");
            if (callTool != null) {
                String server = firstOption(args, "server");
                Day20CallResponse response = server == null
                        ? orchestrator.route(callTool, parseArgs(args))
                        : orchestrator.call(server, callTool, parseArgs(args));
                System.out.println("=== ВЫЗОВ: " + response.server() + "/" + response.tool() + " ===");
                System.out.println("аргументы: " + response.arguments());
                System.out.println("успех: " + response.success());
                System.out.println("результат: ");
                System.out.println(response.result());
                maybeExit(args);
                return;
            }

            String flow = firstOption(args, "flow");
            if (flow != null) {
                Day20FlowResponse response = orchestrator.runFlow(flow, parseArgs(args));
                System.out.println("=== ФЛОУ: " + response.flow() + " ===");
                System.out.println("описание: " + response.description());
                System.out.println("аргументы: " + response.arguments());
                int stepNum = 1;
                for (Day20FlowStepResult step : response.steps()) {
                    System.out.println(stepNum + ". [" + step.server() + "] " + step.tool()
                            + (step.success() ? " — OK" : " — СБОЙ"));
                    System.out.println("   аргументы: " + step.arguments());
                    System.out.println("   результат: " + step.snippet());
                    stepNum++;
                }
                System.out.println("итог: " + response.summary());
                maybeExit(args);
                return;
            }

            String prompt = firstOption(args, "prompt");
            if (prompt != null && !prompt.isBlank()) {
                Day20AgentResponse response = service.submit(prompt);
                System.out.println("=== АГЕНТ / ОРКЕСТРАТОР ===");
                System.out.println("prompt: " + response.prompt());
                System.out.println("intent: " + response.intent());
                System.out.println("server: " + (response.server() == null ? "—" : response.server()));
                System.out.println("tool: " + (response.tool() == null ? "—" : response.tool()));
                System.out.println("arguments: " + (response.arguments() == null ? "—" : response.arguments()));
                System.out.println("tool result: " + (response.toolResult() == null ? "—" : response.toolResult()));
                System.out.println("answer: ");
                System.out.println(response.answer());
                maybeExit(args);
                return;
            }
        } catch (Day20McpException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        } catch (IllegalArgumentException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        }

        System.out.println("Оркестратор MCP-серверов доступен на http://localhost:8080/day20.html, "
                + "MCP-сервер: http://localhost:" + properties.serverPort() + properties.path());
        System.out.println("Проверка: --check | Серверы: --servers | Инструменты: --tools | Флоу: --flows");
        System.out.println("Маршрутизация: --route=<инструмент> [--json=<json>]");
        System.out.println("Вызов сервера: --server=scheduler|market --call=<инструмент> [--json=<json>]");
        System.out.println("Флоу: --flow=market-report|scheduler-brief --json={\"query\":\"ноутбуки\"}");
        System.out.println("Запрос: --prompt=<текст>");
    }

    private Map<String, Object> parseArgs(ApplicationArguments args) {
        String json = firstOption(args, "json");
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return mapper.readValue(json, mapper.getTypeFactory()
                    .constructMapType(Map.class, String.class, Object.class));
        } catch (Exception ex) {
            throw new IllegalArgumentException("Не удалось разобрать --json: " + ex.getMessage());
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