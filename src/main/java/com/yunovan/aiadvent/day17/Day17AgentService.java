package com.yunovan.aiadvent.day17;

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
public class Day17AgentService {

    private static final Logger log = LoggerFactory.getLogger(Day17AgentService.class);

    private static final String HINT = """
            Не удалось распознать, какой инструмент трекера нужен. Примеры запросов:
            - «создай задачу Привезти стол»
            - «создай задачу Написать отчёт, описание: квартальный, для Марии»
            - «покажи задачи» или «покажи задачи в работе»
            - «добавь комментарий к задаче t-a1b2c3d4: проверил, всё ок»
            """;

    private final Day17McpClient client;
    private final LlmClient llm;
    private Day17Connection connection;

    public Day17AgentService(Day17McpClient client, LlmClient llm) {
        this.client = client;
        this.llm = llm;
    }

    public Day17HealthResponse health() {
        try {
            Day17Connection current = connection();
            return new Day17HealthResponse(
                    true, current.serverName(), current.serverVersion(), client.listTools(current).size());
        } catch (Day17McpException ex) {
            connection = null;
            throw ex;
        }
    }

    public List<Day17ToolInfo> tools() {
        try {
            return client.listTools(connection());
        } catch (Day17McpException ex) {
            connection = null;
            throw ex;
        }
    }

    public Day17AgentResponse submit(String prompt) {
        String text = (prompt == null ? "" : prompt).trim();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Запрос не может быть пустым");
        }
        Intent intent = Intent.parse(text);
        if (intent == null) {
            return new Day17AgentResponse(text, null, null, null, false, HINT);
        }
        Day17ToolResult toolResult;
        try {
            toolResult = client.callTool(connection(), intent.tool(), intent.arguments());
        } catch (Day17McpException ex) {
            connection = null;
            throw ex;
        }
        String answer = phrase(text, intent.tool(), toolResult);
        return new Day17AgentResponse(text, intent.tool(), intent.arguments(),
                toolResult.content(), toolResult.isError(), answer);
    }

    private String phrase(String prompt, String tool, Day17ToolResult result) {
        String message = "Пользователь: " + prompt + "\n"
                + "Инструмент MCP " + tool + " вернул результат:\n" + result.content();
        String content = null;
        try {
            LlmReply reply = llm.complete(new CompletionCommand(message,
                    "Ты — ассистент проекта AI Advent. Кратко ответь пользователю по-русски, "
                            + "опираясь на результат инструмента. Если инструмент вернул ошибку — честно скажи об этом.",
                    300, null));
            content = reply == null ? null : reply.content().trim();
        } catch (LlmException ex) {
            log.info("День 17: LLM недоступен, вернём сырой результат инструмента: {}", ex.getMessage());
        }
        if (content == null || content.isBlank()) {
            return (result.isError() ? "Ошибка инструмента: " : "Результат инструмента: ") + result.content();
        }
        return content;
    }

    private Day17Connection connection() {
        if (connection == null) {
            connection = client.connect();
        }
        return connection;
    }

    private record Intent(String tool, Map<String, Object> arguments) {

        private static final List<String> TOOL_CREATE = List.of(
                "создай задачу", "создать задачу", "create a task", "create task");
        private static final List<String> TOOL_LIST = List.of(
                "покажи задачи", "покажи все задачи", "список задач",
                "какие есть задачи", "list tasks", "show tasks");
        private static final List<String> TOOL_COMMENT = List.of(
                "добавь комментарий", "добавить комментарий",
                "оставь комментарий", "add comment");
        private static final Pattern ID_TOKEN = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]*");
        private static final Pattern TASK_ID_TOKEN = Pattern.compile("[tT]-[A-Za-z0-9]{6,}");

        static Intent parse(String prompt) {
            String lower = prompt.toLowerCase(Locale.ROOT);
            if (containsAny(lower, TOOL_COMMENT)) {
                return comment(prompt, lower);
            }
            if (containsAny(lower, TOOL_CREATE)) {
                return create(prompt, lower);
            }
            if (containsAny(lower, TOOL_LIST)) {
                return list(prompt, lower);
            }
            return null;
        }

        private static Intent create(String prompt, String lower) {
            String rest = remainderAfter(prompt, lower, TOOL_CREATE);
            String restLower = rest.toLowerCase(Locale.ROOT);
            String title = rest;
            String description = null;
            String assignee = null;

            Match descriptionMatch = firstMatch(restLower, List.of("описание", "desc"));
            if (descriptionMatch.found()) {
                title = rest.substring(0, descriptionMatch.index());
                String tail = rest.substring(descriptionMatch.index() + descriptionMatch.needle().length());
                Match assigneeMatch = firstMatch(tail.toLowerCase(Locale.ROOT),
                        List.of("для", "назначь", "исполнитель", "assignee"));
                if (assigneeMatch.found()) {
                    description = tail.substring(0, assigneeMatch.index()).trim();
                    assignee = tail.substring(assigneeMatch.index()).trim();
                } else {
                    description = tail.trim();
                }
            } else {
                Match assigneeMatch = firstMatch(restLower, List.of("для", "назначь", "исполнитель", "assignee"));
                if (assigneeMatch.found()) {
                    title = rest.substring(0, assigneeMatch.index());
                    assignee = rest.substring(assigneeMatch.index()).trim();
                }
            }

            title = purge(title);
            description = purge(description);
            assignee = purge(assignee);
            if (title.isBlank()) {
                title = "Задача без названия";
            }
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("title", title);
            if (description != null) {
                args.put("description", description);
            }
            if (assignee != null) {
                args.put("assignee", assignee);
            }
            return new Intent("tracker_create_task", args);
        }

        private static Intent list(String prompt, String lower) {
            Map<String, Object> args = new LinkedHashMap<>();
            String status = null;
            if (lower.contains("в работе") || lower.contains("выполняется") || lower.contains("in progress")) {
                status = "in_progress";
            } else if (lower.contains("готов") || lower.contains("завершен") || lower.contains("сделано")
                    || lower.contains("done")) {
                status = "done";
            } else if (lower.contains("нов") || lower.contains("открыт") || lower.contains("new")) {
                status = "new";
            }
            if (status != null) {
                args.put("status", status);
            }
            return new Intent("tracker_list_tasks", args);
        }

        private static Intent comment(String prompt, String lower) {
            String rest = remainderAfter(prompt, lower, TOOL_COMMENT);
            Matcher taskMatcher = TASK_ID_TOKEN.matcher(rest);
            String taskId = taskMatcher.find() ? taskMatcher.group() : null;
            if (taskId == null) {
                Matcher anyMatcher = ID_TOKEN.matcher(rest);
                taskId = anyMatcher.find() ? anyMatcher.group() : "";
            }
            String text = "";
            int idIndex = rest.indexOf(taskId);
            if (idIndex >= 0) {
                text = rest.substring(idIndex + taskId.length()).replaceFirst("^[\\s,:.\\-]+", "").trim();
            }
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("taskId", taskId);
            args.put("text", text);
            return new Intent("tracker_add_comment", args);
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

        private static Match firstMatch(String text, List<String> needles) {
            int best = Integer.MAX_VALUE;
            String bestNeedle = null;
            for (String needle : needles) {
                int found = text.indexOf(needle);
                if (found >= 0 && found < best) {
                    best = found;
                    bestNeedle = needle;
                }
            }
            return best == Integer.MAX_VALUE ? Match.NONE : new Match(best, bestNeedle);
        }

        private static String purge(String value) {
            if (value == null || value.isBlank()) {
                return null;
            }
            return value.replaceFirst("^[\\s,.:;\\-]+", "").replaceFirst("[\\s,.:;\\-]+$", "").trim();
        }

        private record Match(int index, String needle) {
            static final Match NONE = new Match(-1, null);

            boolean found() {
                return index >= 0;
            }
        }
    }
}