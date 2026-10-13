package com.yunovan.aiadvent.day31;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21Document;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day23.Day23QueryRewriter;
import com.yunovan.aiadvent.day24.Day24Properties;
import com.yunovan.aiadvent.day24.Day24Quote;
import com.yunovan.aiadvent.day24.Day24QuoteEngine;
import com.yunovan.aiadvent.day26.Day26Answer;
import com.yunovan.aiadvent.day26.Day26ChatMessage;
import com.yunovan.aiadvent.day26.Day26ChatOptions;
import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import com.yunovan.aiadvent.day28.Day28RagService;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day31DeveloperAssistantService {

    private static final Logger log = LoggerFactory.getLogger(Day31DeveloperAssistantService.class);

    public static final String SYSTEM =
            "Ты — ассистент разработчика проекта AI Advent. Ты знаешь структуру проекта по его "
                    + "документации (README.md и папка docs), а текущее состояние репозитория получаешь "
                    + "по MCP. Отвечай по-русски, кратко и по делу, опираясь ТОЛЬКО на переданный "
                    + "контекст: документацию и данные git. Для фактов указывай источник (файл). "
                    + "Если в контексте нет ответа — честно скажи об этом, не выдумывай.";

    private static final Set<String> TOOLS = Set.of(
            "git_branch", "git_status", "git_files", "git_diff", "git_log");

    private static final Day24Properties QUOTE_PROPERTIES = new Day24Properties(
            null, null, null, null, null, null, null, null, null, null);

    private final Day31Properties properties;
    private final Day31ProjectIndex index;
    private final Day31McpClient mcp;
    private final Day31GitTool git;
    private final Day26LocalLlmClient localLlm;
    private final Day26Properties localProperties;

    public Day31DeveloperAssistantService(Day31Properties properties, Day31ProjectIndex index,
                                          Day31McpClient mcp, Day31GitTool git,
                                          Day26LocalLlmClient localLlm, Day26Properties localProperties) {
        this.properties = properties;
        this.index = index;
        this.mcp = mcp;
        this.git = git;
        this.localLlm = localLlm;
        this.localProperties = localProperties;
    }

    public Day31HealthResponse health() {
        Day31ProjectIndex.Snapshot snapshot = index.snapshot();
        List<String> documents = snapshot.documents().stream().map(Day21Document::fileName).toList();
        Day31GitInfo info = git.info();
        Day31McpStatus mcpStatus = mcpStatus();
        Day31ModelStatus model = modelStatus();
        String status = info.available() && !documents.isEmpty() ? "ok" : "degraded";
        return new Day31HealthResponse(status, endpoint(), git.repositoryDir().toString(),
                info.branch(), info.dirty(), documents, snapshot.chunks().size(),
                snapshot.corpusChars(), mcpStatus, model);
    }

    public List<Day31DocumentInfo> docs() {
        Day31ProjectIndex.Snapshot snapshot = index.snapshot();
        Map<String, Integer> chunkCounts = new HashMap<>();
        for (Day21Chunk chunk : snapshot.chunks()) {
            chunkCounts.merge(chunk.source(), 1, Integer::sum);
        }
        return snapshot.documents().stream()
                .map(document -> new Day31DocumentInfo(
                        document.source(), document.fileName(), document.title(), document.type(),
                        document.text().length(), chunkCounts.getOrDefault(document.source(), 0)))
                .toList();
    }

    public Day31ToolsResponse tools() {
        try {
            Day31Connection connection = mcp.connect();
            List<Day31ToolInfo> list = mcp.listTools(connection);
            return new Day31ToolsResponse(true, endpoint(), list.size(), list, "");
        } catch (Day31McpException ex) {
            return new Day31ToolsResponse(false, endpoint(), 0, List.of(), ex.getMessage());
        }
    }

    public Day31GitResponse git(String tool, Map<String, Object> arguments) {
        String name = normalizeTool(tool);
        try {
            Day31Connection connection = mcp.connect();
            Day31ToolResult result = mcp.callTool(connection, name,
                    arguments == null ? Map.of() : arguments);
            return new Day31GitResponse(name, !result.isError(), result.content());
        } catch (Day31McpException ex) {
            throw new Day31GitException(ex.getMessage(), ex);
        }
    }

    public Day31HelpResponse help(String question) {
        String questionText = Day28RagService.requireQuestion(question);
        McpGitContext context = mcpGitContext();
        List<Day21SearchHit> hits = index.search(questionText, properties.topK());
        String gitContext = context.available()
                ? context.text()
                : "MCP-инструменты git недоступны: " + context.reason()
                        + "\nЛокальный git: ветка " + git.info().branch();
        String prompt = prompt(questionText, gitContext, hits);
        long started = System.nanoTime();
        try {
            List<Day26ChatMessage> messages = List.of(
                    new Day26ChatMessage("system", SYSTEM),
                    new Day26ChatMessage("user", prompt));
            Day26Answer answer = localLlm.chat(messages,
                    new Day26ChatOptions(properties.temperature(), properties.answerMaxTokens(), null));
            return response(questionText, answer.reply(), false, answer.model(), context, hits,
                    gitContext, answer.latencyMs(), answer.promptTokens(), answer.outputTokens(),
                    answer.tokensPerSecond(), "");
        } catch (Day26LlmException ex) {
            long elapsed = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            log.info("День 31: локальная LLM недоступна, ответ собирается из документации: {}",
                    ex.getMessage());
            return response(questionText, fallbackAnswer(questionText, gitContext, hits), true,
                    localProperties.model(), context, hits, gitContext, elapsed, 0, 0, 0.0,
                    ex.getMessage());
        }
    }

    private Day31HelpResponse response(String question, String answer, boolean fallback, String model,
                                       McpGitContext context, List<Day21SearchHit> hits, String gitContext,
                                       long latencyMs, int promptTokens, int outputTokens,
                                       double tokensPerSecond, String note) {
        Day31GitInfo info = git.info();
        List<String> sourceFiles = Day28RagService.sources(hits);
        return new Day31HelpResponse(question, answer, fallback, model,
                info.available() ? info.branch() : "", info.dirty(), gitContext,
                context.available(), context.tools(), hits.size(), hits, sourceFiles,
                grounding(answer, hits, question), latencyMs, promptTokens, outputTokens,
                tokensPerSecond, note);
    }

    public String prompt(String question, String gitContext, List<Day21SearchHit> hits) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Вопрос разработчика: «").append(question).append("»\n\n");
        prompt.append("Контекст проекта, полученный через MCP:\n").append(gitContext).append("\n\n");
        if (hits.isEmpty()) {
            prompt.append("По запросу в документации проекта (README.md и docs) ничего не найдено.\n");
        } else {
            prompt.append("Документация проекта (README.md и docs, результат поиска по локальному индексу):\n");
            for (Day21SearchHit hit : hits) {
                prompt.append("\n[Источник: ").append(hit.chunk().source())
                        .append(", раздел: «").append(Day28RagService.section(hit))
                        .append("», чанк: ").append(hit.chunk().chunkId()).append("]\n")
                        .append(hit.chunk().text()).append('\n');
            }
        }
        prompt.append("\nПравила ответа:\n")
                .append("- опирайся только на документацию и данные git выше;\n")
                .append("- описывай структуру и устройство проекта по фактам;\n")
                .append("- для каждого факта укажи источник (файл) и раздел;\n")
                .append("- если данных нет — так и скажи.\n");
        return prompt.toString();
    }

    public String fallbackAnswer(String question, String gitContext, List<Day21SearchHit> hits) {
        StringBuilder text = new StringBuilder();
        text.append("Запрос: «").append(question).append("»\n");
        text.append("Ответ собран из документации проекта (локальная LLM недоступна):\n\n");
        text.append("Контекст git:\n").append(gitContext).append("\n\n");
        if (hits.isEmpty()) {
            text.append("По запросу в документации ничего не найдено.");
            return text.toString().trim();
        }
        for (Day21SearchHit hit : hits) {
            text.append("- [").append(hit.chunk().source())
                    .append(", раздел «").append(Day28RagService.section(hit)).append("», score ")
                    .append(String.format(Locale.ROOT, "%.3f", hit.score())).append("]:\n")
                    .append(hit.chunk().text()).append("\n\n");
        }
        return text.toString().trim();
    }

    private McpGitContext mcpGitContext() {
        try {
            Day31Connection connection = mcp.connect();
            List<Day31ToolInfo> tools = mcp.listTools(connection);
            String branch = call(connection, "git_branch");
            String status = call(connection, "git_status");
            String text = "Инструмент MCP git_branch:\n" + branch
                    + "\n\nИнструмент MCP git_status:\n" + status;
            return new McpGitContext(true, tools.size(), text, "");
        } catch (Day31McpException ex) {
            return new McpGitContext(false, 0, "", ex.getMessage());
        }
    }

    private String call(Day31Connection connection, String tool) {
        Day31ToolResult result = mcp.callTool(connection, tool, Map.of());
        if (result.isError()) {
            throw new Day31McpException("Инструмент " + tool + " вернул ошибку: " + result.content());
        }
        return result.content();
    }

    private Day31McpStatus mcpStatus() {
        try {
            Day31Connection connection = mcp.connect();
            List<Day31ToolInfo> list = mcp.listTools(connection);
            return new Day31McpStatus(true, endpoint(), list.size(), "");
        } catch (Day31McpException ex) {
            return new Day31McpStatus(false, endpoint(), 0, ex.getMessage());
        }
    }

    private Day31ModelStatus modelStatus() {
        try {
            String version = localLlm.version();
            boolean installed = localLlm.models().stream()
                    .anyMatch(model -> model.name() != null
                            && model.name().equalsIgnoreCase(localProperties.model()));
            return new Day31ModelStatus(localProperties.endpoint(), localProperties.model(),
                    true, version, installed, "");
        } catch (Day26LlmException ex) {
            return new Day31ModelStatus(localProperties.endpoint(), localProperties.model(),
                    false, "", false, ex.getMessage());
        } catch (RuntimeException ex) {
            return new Day31ModelStatus(localProperties.endpoint(), localProperties.model(),
                    false, "", false, message(ex));
        }
    }

    private Double grounding(String answer, List<Day21SearchHit> hits, String query) {
        if (hits.isEmpty()) {
            return null;
        }
        String matched = Day23QueryRewriter.normalize(query);
        List<Day24Quote> quotes = Day24QuoteEngine.extract(hits, matched, QUOTE_PROPERTIES);
        if (quotes.isEmpty()) {
            return 0.0;
        }
        return 100.0 * Day24QuoteEngine.supportCoverage(answer, quotes);
    }

    public String endpoint() {
        return "http://localhost:" + properties.serverPort() + properties.path();
    }

    private static String normalizeTool(String tool) {
        String name = tool == null ? "" : tool.trim().toLowerCase(Locale.ROOT);
        if (!name.isEmpty() && !name.startsWith("git_")) {
            name = "git_" + name;
        }
        if (!TOOLS.contains(name)) {
            throw new IllegalArgumentException("Неизвестный git-инструмент: " + tool
                    + ". Доступны: git_branch, git_status, git_files, git_diff, git_log");
        }
        return name;
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private record McpGitContext(boolean available, int tools, String text, String reason) {
    }
}
