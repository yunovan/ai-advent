package com.yunovan.aiadvent.day18;

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
public class Day18AgentService {

    private static final Logger log = LoggerFactory.getLogger(Day18AgentService.class);

    private static final String HINT = """
            Не удалось распознать, какой инструмент планировщика нужен. Примеры запросов:
            - «напомни через 10 секунд выпить чай»
            - «собирай данные каждые 3 секунды по events»
            - «пиши сводку каждые 4 секунды по events»
            - «дай сводку по events»
            - «какие задания есть в планировщике»
            """;

    private final Day18McpClient client;
    private final LlmClient llm;
    private Day18Connection connection;

    public Day18AgentService(Day18McpClient client, LlmClient llm) {
        this.client = client;
        this.llm = llm;
    }

    public Day18HealthResponse health() {
        try {
            Day18Connection current = connection();
            return new Day18HealthResponse(
                    true, current.serverName(), current.serverVersion(), client.listTools(current).size());
        } catch (Day18McpException ex) {
            connection = null;
            throw ex;
        }
    }

    public List<Day18ToolInfo> tools() {
        try {
            return client.listTools(connection());
        } catch (Day18McpException ex) {
            connection = null;
            throw ex;
        }
    }

    public Day18AgentResponse submit(String prompt) {
        String text = (prompt == null ? "" : prompt).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Запрос не может быть пустым");
        }
        Intent intent = Intent.parse(text);
        if (intent == null) {
            return new Day18AgentResponse(text, null, null, null, false, HINT);
        }
        Day18ToolResult toolResult;
        try {
            toolResult = client.callTool(connection(), intent.tool(), intent.arguments());
        } catch (Day18McpException ex) {
            connection = null;
            throw ex;
        }
        String answer = phrase(text, intent.tool(), toolResult);
        return new Day18AgentResponse(text, intent.tool(), intent.arguments(),
                toolResult.content(), toolResult.isError(), answer);
    }

    private String phrase(String prompt, String tool, Day18ToolResult result) {
        String message = "Пользователь: " + prompt + "\n"
                + "Инструмент MCP " + tool + " вернул результат:\n" + result.content();
        String content = null;
        try {
            LlmReply reply = llm.complete(new CompletionCommand(message,
                    "Ты — ассистент проекта AI Advent. Кратко ответь пользователю по-русски, "
                            + "опираясь на результат инструмента планировщика. Если инструмент вернул "
                            + "ошибку — честно скажи об этом.",
                    300, null));
            content = reply == null ? null : reply.content().trim();
        } catch (LlmException ex) {
            log.info("День 18: LLM недоступен, вернём сырой результат инструмента: {}", ex.getMessage());
        }
        if (content == null || content.isBlank()) {
            return (result.isError() ? "Ошибка инструмента: " : "Результат инструмента: ") + result.content();
        }
        return content;
    }

    private Day18Connection connection() {
        if (connection == null) {
            connection = client.connect();
        }
        return connection;
    }

    private record Intent(String tool, Map<String, Object> arguments) {

        private static final Pattern WHEN = Pattern.compile(
                "(?:через|за|каждые|каждую|каждый|every)\\s+(\\d+)\\s+"
                        + "(секунд[аыу]?|сек\\b|минут[аыу]?|мин\\b|час(?:ов|а)?|"
                        + "seconds?|minutes?|hours?)",
                Pattern.CASE_INSENSITIVE);
        private static final Pattern URL_TOKEN = Pattern.compile("https?://[^\\s,.]+");
        private static final Pattern FEED_AFTER = Pattern.compile(
                "(?:по\\s+тем[еу]|по\\s+каналу|данные\\s+по|сводк[уа]\\s+по|по)\\s+([\\p{L}0-9_-]+)",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        private static final List<String> REMINDERS = List.of("напомни", "напоминание", "remind");
        private static final List<String> COLLECTORS = List.of(
                "собирай", "собирать", "коллектор", "каждые", "периодическ", "collect");
        private static final List<String> SUMMARIES = List.of(
                "сводка", "сводку", "обзор", "статистика", "итог", "агрегируй", "summary");
        private static final List<String> LISTS = List.of(
                "какие задания", "покажи задания", "список заданий", "что запланировано",
                "задания по расписанию", "jobs");

        static Intent parse(String prompt) {
            String lower = prompt.toLowerCase(Locale.ROOT);
            if (containsAny(lower, REMINDERS)) {
                return reminder(prompt);
            }
            Matcher when = WHEN.matcher(prompt);
            if (when.find() && containsAny(lower, COLLECTORS)) {
                return collector(prompt, when);
            }
            if (containsAny(lower, SUMMARIES)) {
                return summary(prompt);
            }
            if (containsAny(lower, LISTS)) {
                return new Intent("scheduler_list_jobs", Map.of());
            }
            return null;
        }

        private static Intent reminder(String prompt) {
            String lower = prompt.toLowerCase(Locale.ROOT);
            String rest = remainderAfter(prompt, lower, REMINDERS);
            Matcher when = WHEN.matcher(rest);
            String topic;
            int delaySeconds = 10;
            if (when.find()) {
                delaySeconds = parseDuration(when.group(1), when.group(2));
                topic = rest.substring(0, when.start()) + rest.substring(when.end());
            } else {
                topic = rest;
            }
            topic = topic.replaceFirst("^[\\s,\\-:]+", "").replaceFirst("\\s*[.\\-:]+$", "").trim();
            if (topic.isBlank()) {
                topic = "Напоминание";
            }
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("topic", topic);
            args.put("delaySeconds", delaySeconds);
            return new Intent("scheduler_add_reminder", args);
        }

        private static Intent collector(String prompt, Matcher when) {
            int seconds = parseDuration(when.group(1), when.group(2));
            String restLower = prompt.toLowerCase(Locale.ROOT);
            String feed = feedName(restLower);
            boolean digest = restLower.contains("сводк") || restLower.contains("summary")
                    || restLower.contains("агрегир") || restLower.startsWith("пиши сводку")
                    || restLower.contains("сводку каждые");
            String url = null;
            Matcher urlMatcher = URL_TOKEN.matcher(prompt);
            if (urlMatcher.find() && !digest) {
                url = urlMatcher.group();
            }
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("feed", digest ? "digest" : (feed == null ? "events" : feed));
            args.put("periodSeconds", seconds);
            if (url != null) {
                args.put("url", url);
            }
            if (digest) {
                args.put("sourceFeed", feed == null ? "events" : feed);
            }
            return new Intent("scheduler_add_collector", args);
        }

        private static Intent summary(String prompt) {
            String restLower = prompt.toLowerCase(Locale.ROOT);
            Map<String, Object> args = new LinkedHashMap<>();
            String feed = feedName(restLower);
            if (feed != null) {
                args.put("feed", feed);
            }
            Matcher when = WHEN.matcher(prompt);
            if (when.find()) {
                args.put("sinceSeconds", parseDuration(when.group(1), when.group(2)));
            }
            return new Intent("scheduler_summary", args);
        }

        private static String feedName(String lower) {
            Matcher matcher = FEED_AFTER.matcher(lower);
            return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
        }

        private static int parseDuration(String number, String unit) {
            int value = Integer.parseInt(number.trim());
            String u = unit.toLowerCase(Locale.ROOT);
            if (u.startsWith("час") || u.startsWith("hour")) {
                return value * 3600;
            }
            if (u.startsWith("мин") || u.startsWith("minute")) {
                return value * 60;
            }
            return value;
        }

        private static boolean containsAny(String lower, List<String> needles) {
            for (String needle : needles) {
                if (lower.contains(needle)) {
                    return true;
                }
            }
            return false;
        }

        private static String remainderAfter(String prompt, String lower, List<String> markers) {
            int best = -1;
            int bestLength = 0;
            for (String marker : markers) {
                int found = lower.indexOf(marker);
                if (found >= 0 && (best < 0 || found < best)) {
                    best = found;
                    bestLength = marker.length();
                }
            }
            if (best < 0) {
                return prompt.trim();
            }
            return prompt.substring(best + bestLength).replaceFirst("^[\\s,\\-:]+", "").trim();
        }
    }
}