package com.yunovan.aiadvent.day20;

import com.yunovan.aiadvent.day18.Day18Connection;
import com.yunovan.aiadvent.day18.Day18McpClient;
import com.yunovan.aiadvent.day18.Day18McpException;
import com.yunovan.aiadvent.day18.Day18Properties;
import com.yunovan.aiadvent.day18.Day18ToolInfo;
import com.yunovan.aiadvent.day18.Day18ToolResult;
import com.yunovan.aiadvent.day20.Day20FlowDefinition.Day20FlowStepDefinition;
import com.yunovan.aiadvent.day19.Day19Connection;
import com.yunovan.aiadvent.day19.Day19McpClient;
import com.yunovan.aiadvent.day19.Day19McpException;
import com.yunovan.aiadvent.day19.Day19Properties;
import com.yunovan.aiadvent.day19.Day19ToolInfo;
import com.yunovan.aiadvent.day19.Day19ToolResult;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day20Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Day20Orchestrator.class);

    private static final Pattern STEP_REF = Pattern.compile("\\{(\\d+)}");
    private static final Pattern NAMED_REF = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)}");

    private static final List<Day20FlowDefinition> FLOWS = List.of(
            new Day20FlowDefinition(
                    "market-report",
                    "Кросс-серверный флоу: search и summarize на витрине (market), saveToFile, "
                            + "затем scheduler_add_reminder на планировщике (scheduler) с путём к файлу.",
                    List.of(
                            step("market", "search", Map.of("query", "{query}", "maxResults", "5")),
                            step("market", "summarize", Map.of(
                                    "query", "{query}", "data", "{0}", "format", "{format}")),
                            step("market", "saveToFile", Map.of(
                                    "data", "{0}", "summary", "{1}", "format", "{format}",
                                    "fileName", "{fileName}")),
                            step("scheduler", "scheduler_add_reminder", Map.of(
                                    "topic", "Отчёт по запросу «{query}» сохранён: {2}",
                                    "delaySeconds", "1")))),
            new Day20FlowDefinition(
                    "scheduler-brief",
                    "Флоу на планировщике (scheduler): scheduler_add_collector создаёт периодический сбор, "
                            + "затем scheduler_summary возвращает сводку по этому потоку.",
                    List.of(
                            step("scheduler", "scheduler_add_collector", Map.of(
                                    "feed", "{feed}", "periodSeconds", "{periodSeconds}",
                                    "url", "{url}")),
                            step("scheduler", "scheduler_summary", Map.of(
                                    "feed", "{feed}", "sinceSeconds", "{sinceSeconds}")))));

    private final Day18McpClient schedulerClient;
    private final Day19McpClient marketClient;
    private final Day18Properties schedulerProperties;
    private final Day19Properties marketProperties;

    private Day18Connection schedulerConnection;
    private Day19Connection marketConnection;

    public Day20Orchestrator(Day18McpClient schedulerClient, Day19McpClient marketClient,
                             Day18Properties schedulerProperties, Day19Properties marketProperties) {
        this.schedulerClient = schedulerClient;
        this.marketClient = marketClient;
        this.schedulerProperties = schedulerProperties;
        this.marketProperties = marketProperties;
    }

    public Day20HealthResponse health() {
        List<Day20ServerInfo> servers = new ArrayList<>();
        int toolCount = 0;
        servers.add(schedulerServer());
        servers.add(marketServer());
        boolean connected = servers.stream().allMatch(Day20ServerInfo::connected);
        toolCount = servers.stream().mapToInt(Day20ServerInfo::toolCount).sum();
        return new Day20HealthResponse(connected, servers, toolCount);
    }

    private Day20ServerInfo schedulerServer() {
        Day18Connection connection = null;
        try {
            connection = schedulerConnection();
            int count = schedulerClient.listTools(connection).size();
            return new Day20ServerInfo(Day20Registry.SCHEDULER_SERVER,
                    connection.serverName(), connection.serverVersion(), count, true);
        } catch (RuntimeException ex) {
            log.info("День 20: планировщик недоступен: {}", ex.getMessage());
            schedulerConnection = null;
            return new Day20ServerInfo(Day20Registry.SCHEDULER_SERVER,
                    schedulerProperties.name(), schedulerProperties.version(), 0, false);
        }
    }

    private Day20ServerInfo marketServer() {
        Day19Connection connection = null;
        try {
            connection = marketConnection();
            int count = marketClient.listTools(connection).size();
            return new Day20ServerInfo(Day20Registry.MARKET_SERVER,
                    connection.serverName(), connection.serverVersion(), count, true);
        } catch (RuntimeException ex) {
            log.info("День 20: витрина недоступна: {}", ex.getMessage());
            marketConnection = null;
            return new Day20ServerInfo(Day20Registry.MARKET_SERVER,
                    marketProperties.name(), marketProperties.version(), 0, false);
        }
    }

    public List<Day20ToolEntry> tools() {
        List<Day20ToolEntry> result = new ArrayList<>();
        Day18Connection scheduler = schedulerConnection();
        for (Day18ToolInfo tool : schedulerClient.listTools(scheduler)) {
            result.add(new Day20ToolEntry(Day20Registry.SCHEDULER_SERVER, tool.name(), tool.description()));
        }
        Day19Connection market = marketConnection();
        for (Day19ToolInfo tool : marketClient.listTools(market)) {
            result.add(new Day20ToolEntry(Day20Registry.MARKET_SERVER, tool.name(), tool.description()));
        }
        return result;
    }

    public List<Day20FlowDefinition> flows() {
        return FLOWS;
    }

    public Day20CallResponse route(String tool, Map<String, Object> arguments) {
        String server = Day20Registry.serverFor(tool);
        return call(server, tool, arguments == null ? Map.of() : arguments);
    }

    public String callText(String server, String tool, Map<String, Object> arguments) {
        return call(server, tool, arguments).result();
    }

    public Day20CallResponse call(String server, String tool, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        if (Day20Registry.SCHEDULER_SERVER.equals(server)) {
            try {
                Day18ToolResult result = schedulerClient.callTool(schedulerConnection(), tool, args);
                return new Day20CallResponse(server, tool, args,
                        !result.isError() && !result.content().isBlank(), result.content());
            } catch (Day18McpException ex) {
                throw new Day20McpException(
                        "Сервер «" + server + "» (планировщик) недоступен: " + ex.getMessage(), ex);
            }
        }
        if (Day20Registry.MARKET_SERVER.equals(server)) {
            try {
                Day19ToolResult result = marketClient.callTool(marketConnection(), tool, args);
                return new Day20CallResponse(server, tool, args,
                        !result.isError() && !result.content().isBlank(), result.content());
            } catch (Day19McpException ex) {
                throw new Day20McpException(
                        "Сервер «" + server + "» (витрина) недоступен: " + ex.getMessage(), ex);
            }
        }
        throw new IllegalArgumentException("Неизвестный сервер «" + server + "». Зарегистрированные серверы: "
                + String.join(", ", Day20Registry.allServerIds()));
    }

    public Day20FlowResponse runFlow(String flowName, Map<String, Object> arguments) {
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        Day20FlowDefinition flow = flow(flowName);
        if (flow == null) {
            throw new IllegalArgumentException("Флоу «" + flowName + "» не найден. Доступные флоу: "
                    + flows().stream().map(Day20FlowDefinition::key).toList());
        }
        List<String> outputs = new ArrayList<>();
        List<Day20FlowStepResult> steps = new ArrayList<>();
        for (Day20FlowStepDefinition step : flow.steps()) {
            Map<String, Object> resolved = resolve(step.arguments(), outputs, args);
            Day20CallResponse response = call(step.server(), step.tool(), resolved);
            String output = response.result();
            boolean success = response.success();
            outputs.add(output);
            steps.add(new Day20FlowStepResult(step.server(), step.tool(), resolved, success,
                    truncate(output, 140)));
            log.info("День 20: флоу {} шаг {} на сервере {} → {}", flow.key(), step.tool(),
                    step.server(), success ? "OK" : "СБОЙ");
        }
        long success = steps.stream().filter(Day20FlowStepResult::success).count();
        String summary = "Флоу «" + flow.key() + "» — шагов: " + steps.size() + ", успешно: "
                + success + "/" + steps.size();
        return new Day20FlowResponse(flow.key(), flow.description(), args, steps, summary);
    }

    private Day20FlowDefinition flow(String key) {
        for (Day20FlowDefinition flow : FLOWS) {
            if (flow.key().equals(key)) {
                return flow;
            }
        }
        return null;
    }

    private static Day20FlowStepDefinition step(String server, String tool, Map<String, String> arguments) {
        return new Day20FlowStepDefinition(server, tool, arguments);
    }

    private static Map<String, Object> resolve(Map<String, String> templates,
                                               List<String> outputs, Map<String, Object> named) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : templates.entrySet()) {
            String value = entry.getValue();
            Matcher stepMatcher = STEP_REF.matcher(value);
            StringBuffer stepBuffer = new StringBuffer();
            while (stepMatcher.find()) {
                int index = parseIndex(stepMatcher.group(1));
                String replacement = index >= 0 && index < outputs.size() ? outputs.get(index) : stepMatcher.group();
                stepMatcher.appendReplacement(stepBuffer, Matcher.quoteReplacement(replacement));
            }
            stepMatcher.appendTail(stepBuffer);
            String stepResolved = stepBuffer.toString();
            Matcher namedMatcher = NAMED_REF.matcher(stepResolved);
            StringBuffer namedBuffer = new StringBuffer();
            while (namedMatcher.find()) {
                String key = namedMatcher.group(1);
                String replacement = named.containsKey(key)
                        ? String.valueOf(named.get(key)) : namedMatcher.group();
                namedMatcher.appendReplacement(namedBuffer, Matcher.quoteReplacement(replacement));
            }
            namedMatcher.appendTail(namedBuffer);
            resolved.put(entry.getKey(), namedBuffer.toString());
        }
        return resolved;
    }

    private static int parseIndex(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private synchronized Day18Connection schedulerConnection() {
        if (schedulerConnection == null) {
            try {
                schedulerConnection = schedulerClient.connect();
            } catch (Day18McpException ex) {
                throw new Day20McpException("Планировщик недоступен: " + ex.getMessage(), ex);
            }
        }
        return schedulerConnection;
    }

    private synchronized Day19Connection marketConnection() {
        if (marketConnection == null) {
            try {
                marketConnection = marketClient.connect();
            } catch (Day19McpException ex) {
                throw new Day20McpException("Витрина недоступна: " + ex.getMessage(), ex);
            }
        }
        return marketConnection;
    }
}