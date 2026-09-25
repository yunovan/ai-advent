package com.yunovan.aiadvent.day19;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmReply;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day19AgentService {

    private static final Logger log = LoggerFactory.getLogger(Day19AgentService.class);

    private static final String SEARCH_TOOL = "search";
    private static final String SUMMARIZE_TOOL = "summarize";
    private static final String SAVE_TOOL = "saveToFile";

    private static final List<String> PIPELINE_MARKERS = List.of("сохрани", "в файл", "пайплайн",
            "полный цикл", "весь процесс", "pipeline", "save");
    private static final List<String> COMPARE_MARKERS = List.of("сравни", "сравнение", "сводную",
            "таблицу", "summarize", "summary");
    private static final List<String> SEARCH_MARKERS = List.of("найди", "найти", "ищи", "поиск",
            "поищи", "подбери", "search");
    private static final List<String> STOP_WORDS = List.of("и", "в", "по", "на", "про", ",", "для",
            "мне", "нужно", "посмотреть", "сделай", "таблицу", "таблица", "сводное", "сравнение",
            "сводн", "список", "файл", "файла", "файлы");
    private static final java.util.regex.Pattern FORMAT_PATTERN =
            java.util.regex.Pattern.compile("(csv|json|markdown|md|txt)");
    private static final java.util.regex.Pattern FILE_CLAUSE_PATTERN = java.util.regex.Pattern.compile(
            "(?:в файл|file)\\s*[\\p{L}\\p{N}_\\-]{0,40}",
            java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE);

    private final Day19McpClient client;
    private final LlmClient llm;
    private final Day19MarketService market;
    private Day19Connection connection;

    public Day19AgentService(Day19McpClient client, LlmClient llm, Day19MarketService market) {
        this.client = client;
        this.llm = llm;
        this.market = market;
    }

    public Day19HealthResponse health() {
        try {
            Day19Connection current = connection();
            return new Day19HealthResponse(true, current.serverName(), current.serverVersion(),
                    client.listTools(current).size());
        } catch (Day19McpException ex) {
            connection = null;
            throw ex;
        }
    }

    public List<Day19ToolInfo> tools() {
        try {
            return client.listTools(connection());
        } catch (Day19McpException ex) {
            connection = null;
            throw ex;
        }
    }

    private Day19Connection connection() {
        if (connection == null) {
            connection = client.connect();
        }
        return connection;
    }

    public Day19PipelineResponse pipeline(String query, String format, String fileName) {
        String fmt = normalizeFormat(format);
        Day19Connection connection = connection();
        List<Day19PipelineStep> steps = new ArrayList<>();

        Map<String, Object> searchArgs = new LinkedHashMap<>();
        searchArgs.put("query", query);
        Day19ToolResult search = client.callTool(connection, SEARCH_TOOL, searchArgs);
        String data = search.content();
        List<Day19Product> products = market.parseProducts(data);
        checkStep(steps, SEARCH_TOOL, data, () -> products != null && !products.isEmpty());
        log.info("День 19: шаг search → {} товаров", products == null ? 0 : products.size());

        Map<String, Object> summarizeArgs = new LinkedHashMap<>();
        summarizeArgs.put("query", query);
        summarizeArgs.put("data", data);
        summarizeArgs.put("format", fmt);
        Day19ToolResult summary = client.callTool(connection, SUMMARIZE_TOOL, summarizeArgs);
        checkStep(steps, SUMMARIZE_TOOL, summary.content(),
                () -> summary.content().contains("Сравнение по запросу"));

        Map<String, Object> saveArgs = new LinkedHashMap<>();
        saveArgs.put("data", data);
        saveArgs.put("summary", summary.content());
        saveArgs.put("format", fmt);
        saveArgs.put("fileName", fileName);
        Day19ToolResult saved = client.callTool(connection, SAVE_TOOL, saveArgs);
        Day19SavedFile savedFile = parseSavedFile(saved.content());
        checkStep(steps, SAVE_TOOL, saved.content(), () -> savedFile != null && savedFile.bytes() > 0);

        return new Day19PipelineResponse(query, fmt, steps, savedFile);
    }

    public Day19AgentResponse submit(String prompt) {
        String promptText = prompt == null ? "" : prompt.trim();
        if (promptText.isBlank()) {
            throw new IllegalArgumentException("Запрос не может быть пустым");
        }
        Intent intent = parseIntent(promptText);
        Day19PipelineResponse pipeline = null;
        String toolResult = null;
        switch (intent.kind()) {
            case "pipeline" -> {
                String format = intent.format() == null ? "markdown" : intent.format();
                pipeline = pipeline(intent.query(), format, intent.fileName());
                toolResult = "Файл сохранён: " + pipeline.saved().path();
            }
            case "compare" -> {
                String format = intent.format() == null ? "markdown" : intent.format();
                String data = market.productsJson(market.search(intent.query(), null, null, "rating"));
                toolResult = market.summarize(intent.query(), data, format);
            }
            default -> toolResult = market.productsJson(market.search(intent.query(), null, null, "rating"));
        }
        String answer = phrase(promptText, intent.tool(), toolResult);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", intent.query());
        if ("compare".equals(intent.kind()) || "pipeline".equals(intent.kind())) {
            args.put("format", intent.format() == null ? "markdown" : intent.format());
        }
        if (intent.fileName() != null) {
            args.put("fileName", intent.fileName());
        }
        return new Day19AgentResponse(promptText, intent.kind(), intent.tool(), args, toolResult, answer);
    }

    private String phrase(String prompt, String tool, String toolResult) {
        String message = "Пользователь: " + prompt + "\n"
                + "Инструмент MCP " + tool + " вернул результат:\n" + toolResult;
        String content = null;
        try {
            LlmReply reply = llm.complete(new CompletionCommand(message,
                    "Ты — ассистент проекта AI Advent. Кратко ответь пользователю по-русски, "
                            + "опираясь на результат инструмента поисковой витрины. Если инструмент вернул "
                            + "ошибку — честно скажи об этом.",
                    300, null));
            content = reply == null ? null : reply.content().trim();
        } catch (LlmException ex) {
            log.info("День 19: LLM недоступен, вернём сырой результат инструмента: {}", ex.getMessage());
        }
        if (content == null || content.isBlank()) {
            return "Результат инструмента " + tool + ":\n" + toolResult;
        }
        return content;
    }

    private void checkStep(List<Day19PipelineStep> steps, String tool, String content,
                           java.util.function.BooleanSupplier valid) {
        boolean success;
        try {
            success = content != null && !content.isBlank() && valid.getAsBoolean();
        } catch (RuntimeException ex) {
            success = false;
        }
        String note = success ? switch (tool) {
            case SEARCH_TOOL -> "получено " + countProducts(content) + " товаров";
            case SUMMARIZE_TOOL -> "таблица готова к сохранению";
            default -> "файл записан";
        } : "данные не прошли проверку передачи";
        steps.add(new Day19PipelineStep(tool, success, note));
    }

    private int countProducts(String data) {
        try {
            JsonNode json = new ObjectMapper().readTree(data);
            return json.path("products").size();
        } catch (Exception ex) {
            return 0;
        }
    }

    private Day19SavedFile parseSavedFile(String content) {
        try {
            JsonNode json = new ObjectMapper().readTree(content);
            if (!json.isObject()) {
                return null;
            }
            return new Day19SavedFile(
                    json.path("fileName").asText(),
                    json.path("path").asText(),
                    json.path("format").asText(),
                    json.path("bytes").asLong(),
                    json.path("content").asText(null));
        } catch (Exception ex) {
            return null;
        }
    }

    private static String normalizeFormat(String format) {
        String fmt = format == null ? "markdown" : format.trim().toLowerCase(Locale.ROOT);
        return "md".equals(fmt) ? "markdown" : fmt;
    }

    private Intent parseIntent(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        String fmt = null;
        java.util.regex.Matcher matcher = FORMAT_PATTERN.matcher(lower);
        if (matcher.find()) {
            fmt = "md".equals(matcher.group(1)) ? "markdown" : matcher.group(1);
        }
        String fileName = null;
        java.util.regex.Matcher fileMatcher = java.util.regex.Pattern
                .compile("(?:в файл|file)(?:\\s+([\\p{L}\\p{N}_\\-]{1,40}))?",
                        java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.UNICODE_CASE)
                .matcher(prompt);
        if (fileMatcher.find() && fileMatcher.group(1) != null) {
            String candidate = fileMatcher.group(1);
            if (!isFormatWord(candidate, fmt)) {
                fileName = candidate;
            }
        }
        for (String marker : PIPELINE_MARKERS) {
            if (lower.contains(marker)) {
                return new Intent("pipeline", SAVE_TOOL,
                        queryAfterMarker(prompt, marker, fmt, true), fmt, fileName);
            }
        }
        for (String marker : COMPARE_MARKERS) {
            if (lower.contains(marker)) {
                return new Intent("compare", SUMMARIZE_TOOL,
                        queryAfterMarker(prompt, marker, fmt, false), fmt, null);
            }
        }
        for (String marker : SEARCH_MARKERS) {
            if (lower.contains(marker)) {
                return new Intent("search", SEARCH_TOOL,
                        queryAfterMarker(prompt, marker, fmt, false), null, null);
            }
        }
        String hint = "Сформулируйте запрос иначе. Примеры:\n"
                + "  найди ноутбуки\n"
                + "  сравни смартфоны в таблицу\n"
                + "  сохрани телевизоры в файл";
        throw new IllegalArgumentException(hint);
    }

    private static String queryAfterMarker(String prompt, String marker, String format,
                                           boolean removeFileClause) {
        String trimmed = prompt.toLowerCase(Locale.ROOT);
        int index = trimmed.indexOf(marker);
        String after = index + marker.length() < trimmed.length() ? prompt.substring(index + marker.length()) : "";
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

    private record Intent(String kind, String tool, String query, String format, String fileName) {
    }
}