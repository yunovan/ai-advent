package com.yunovan.aiadvent.day20;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmReply;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day20AgentService {

    private static final Logger log = LoggerFactory.getLogger(Day20AgentService.class);

    private static final List<String> FLOW_MARKERS = List.of(
            "полный отчёт", "составь отчёт", "собери отчёт", "отчёт и сохрани",
            "сводку и сохрани", "сводку и файл", "с напоминанием", "в файл и напомни",
            "флоу", "market-report", "flow", "сохрани");
    private static final List<String> SCHEDULER_MARKERS = List.of(
            "сводку по событиям", "сводка по событиям", "по процессам", "процессы",
            "джобы", "jobs", "статус планировщика", "напомни", "напоминание",
            "планировщ", "событиям", "scheduler");
    private static final List<String> SCHEDULER_LIST_MARKERS = List.of(
            "список", "джобы", "jobs", "list");
    private static final List<String> MARKET_MARKERS = List.of(
            "сравни", "таблица", "сводной", "товарам", "товары", "найди",
            "поиск", "поищи", "витрина", "market");
    private static final List<String> MARKET_SUMMARIZE_MARKERS = List.of(
            "сравни", "таблица", "сводной", "сводную");
    private static final List<String> STOP_WORDS = List.of(
            "и", "в", "по", "на", "про", "для", "мне", "нужно", "сделай",
            "потом", "затем", "после", "таблицу", "таблица", "сводное", "сводную",
            "сводка", "отчёт", "отчета", "отчёт", "файл", "файла", "файлы",
            "флоу", "сервер", "оркестр", "витрины", "планировщика", "событиям",
            "напоминание", "пожалуйста", "сохрани");
    private static final Pattern FORMAT_PATTERN =
            Pattern.compile("(csv|json|markdown|md|txt)");
    private static final Pattern FILE_CLAUSE_PATTERN = Pattern.compile(
            "(?:в файл|file)\\s*[\\p{L}\\p{N}_\\-]{0,40}",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final Day20Orchestrator orchestrator;
    private final LlmClient llm;

    public Day20AgentService(Day20Orchestrator orchestrator, LlmClient llm) {
        this.orchestrator = orchestrator;
        this.llm = llm;
    }

    public Day20AgentResponse submit(String prompt) {
        String promptText = prompt == null ? "" : prompt.trim();
        if (promptText.isBlank()) {
            throw new IllegalArgumentException("Запрос не может быть пустым");
        }
        Intent intent = parseIntent(promptText);
        String toolResult;
        Day20FlowResponse flow = null;
        Day20CallResponse routed = null;
        switch (intent.kind()) {
            case "flow" -> {
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("query", intent.query());
                args.put("format", intent.format() == null ? "markdown" : intent.format());
                if (intent.fileName() != null) {
                    args.put("fileName", intent.fileName());
                }
                flow = orchestrator.runFlow("market-report", args);
                toolResult = flow.summary();
            }
            case "scheduler" -> {
                String tool = intent.tool();
                Map<String, Object> args = Map.of();
                routed = orchestrator.route(tool, args);
                toolResult = routed.result();
            }
            default -> toolResult = marketResult(intent);
        }
        String tool = intent.tool();
        if (flow != null) {
            tool = flow.flow();
        } else if (routed != null) {
            tool = routed.tool();
        }
        String answer = phrase(promptText, intent.server(), tool, toolResult);
        return new Day20AgentResponse(promptText, intent.kind(), intent.server(), tool,
                intent.arguments(), toolResult, answer);
    }

    private String marketResult(Intent intent) {
        Map<String, Object> queryArgs = Map.of("query", intent.query(), "maxResults", "5");
        String searchText = orchestrator.callText(Day20Registry.MARKET_SERVER, "search", queryArgs);
        if (!"summarize".equals(intent.tool())) {
            return searchText;
        }
        Map<String, Object> summarizeArgs = new LinkedHashMap<>();
        summarizeArgs.put("query", intent.query());
        summarizeArgs.put("data", searchText);
        summarizeArgs.put("format", intent.format() == null ? "markdown" : intent.format());
        return orchestrator.callText(Day20Registry.MARKET_SERVER, "summarize", summarizeArgs);
    }

    private String phrase(String prompt, String server, String tool, String toolResult) {
        String context = server == null || server.isBlank() ? "оркестратор" : server;
        String message = "Пользователь: " + prompt + "\n"
                + "Оркестратор выбрал MCP-сервер «" + context + "» и инструмент «" + tool
                + "», который вернул результат:\n" + toolResult;
        String content = null;
        try {
            LlmReply reply = llm.complete(new CompletionCommand(message,
                    "Ты — ассистент проекта AI Advent, который управляет несколькими MCP-серверами. "
                            + "Кратко ответь пользователю по-русски, опираясь на результат инструмента. "
                            + "Если инструмент вернул ошибку — честно скажи об этом.",
                    300, null));
            content = reply == null ? null : reply.content().trim();
        } catch (LlmException ex) {
            log.info("День 20: LLM недоступен, вернём сырой результат инструмента: {}", ex.getMessage());
        }
        if (content == null || content.isBlank()) {
            return "Результат инструмента " + tool + " (сервер «" + context + "»):\n" + toolResult;
        }
        return content;
    }

    private Intent parseIntent(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        String format = null;
        Matcher formatMatcher = FORMAT_PATTERN.matcher(lower);
        if (formatMatcher.find()) {
            format = "md".equals(formatMatcher.group(1)) ? "markdown" : formatMatcher.group(1);
        }
        String fileName = null;
        Matcher fileMatcher = Pattern.compile(
                "(?:в файл|file)(?:\\s+([\\p{L}\\p{N}_\\-]{1,40}))?",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(prompt);
        if (fileMatcher.find() && fileMatcher.group(1) != null
                && !isFormatWord(fileMatcher.group(1), format)) {
            fileName = fileMatcher.group(1);
        }
        for (String marker : FLOW_MARKERS) {
            if (lower.contains(marker)) {
                String query = queryAfterMarker(prompt, marker, format, true);
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("query", query);
                args.put("format", format == null ? "markdown" : format);
                if (fileName != null) {
                    args.put("fileName", fileName);
                }
                return new Intent("flow", null, "market-report", args, query, format, fileName);
            }
        }
        for (String marker : SCHEDULER_MARKERS) {
            if (lower.contains(marker)) {
                boolean list = SCHEDULER_LIST_MARKERS.stream().anyMatch(lower::contains);
                String tool = list ? "scheduler_list_jobs" : "scheduler_summary";
                return new Intent("scheduler", Day20Registry.SCHEDULER_SERVER, tool,
                        Map.of(), "", null, null);
            }
        }
        for (String marker : MARKET_MARKERS) {
            if (lower.contains(marker)) {
                boolean summarize = MARKET_SUMMARIZE_MARKERS.stream().anyMatch(lower::contains);
                String tool = summarize ? "summarize" : "search";
                String query = queryAfterMarker(prompt, marker, format, false);
                Map<String, Object> args = new LinkedHashMap<>();
                args.put("query", query);
                if (summarize) {
                    args.put("format", format == null ? "markdown" : format);
                }
                return new Intent("market", Day20Registry.MARKET_SERVER, tool,
                        args, query, format, null);
            }
        }
        String hint = "Сформулируйте запрос иначе. Примеры:\n"
                + "  составь полный отчёт по ноутбукам и сохрани в файл csv\n"
                + "  сводку по событиям планировщика\n"
                + "  сравни смартфоны в таблицу";
        throw new IllegalArgumentException(hint);
    }

    private String queryAfterMarker(String prompt, String marker, String format,
                                    boolean removeFileClause) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(marker);
        String after = index + marker.length() < lower.length()
                ? prompt.substring(index + marker.length()) : "";
        if (removeFileClause) {
            after = FILE_CLAUSE_PATTERN.matcher(after).replaceAll(" ");
        }
        StringBuilder query = new StringBuilder();
        for (String token : after.split("\\s+")) {
            String clean = token.replaceAll("[,.!?;:()\"«»]", "");
            if (clean.isBlank() || STOP_WORDS.contains(clean.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (format != null && (clean.equalsIgnoreCase(format) || clean.equalsIgnoreCase("md"))) {
                continue;
            }
            query.append(clean).append(' ');
        }
        return query.toString().trim();
    }

    private static boolean isFormatWord(String candidate, String format) {
        String value = candidate.toLowerCase(Locale.ROOT);
        if (format != null && value.equalsIgnoreCase(format)) {
            return true;
        }
        return value.equals("md") || value.equals("markdown") || value.equals("txt")
                || value.equals("text") || value.equals("csv") || value.equals("json");
    }

    private record Intent(String kind, String server, String tool, Map<String, Object> arguments,
                          String query, String format, String fileName) {
    }
}