package com.yunovan.aiadvent.day33;

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
public class Day33SupportService {

    private static final Logger log = LoggerFactory.getLogger(Day33SupportService.class);

    public static final String SYSTEM =
            "Ты — ассистент службы поддержки продукта «Облако Задач». Ты помогаешь оператору и "
                    + "клиенту: отвечаешь на вопросы о продукте, опираясь на базу знаний (FAQ и "
                    + "документацию), и учитываешь контекст пользователя и обращения, полученный из "
                    + "CRM по MCP. Отвечай по-русски, кратко и дружелюбно. Для фактов указывай "
                    + "источник (файл). Если в контексте нет ответа — честно скажи об этом, не выдумывай.";

    public static final Set<String> TOOLS = Set.of(
            "crm_user", "crm_ticket", "crm_user_tickets", "crm_search_tickets", "crm_open_tickets");

    private static final Day24Properties QUOTE_PROPERTIES = new Day24Properties(
            null, null, null, null, null, null, null, null, null, null);

    private final Day33Properties properties;
    private final Day33SupportIndex index;
    private final Day33CrmStore crm;
    private final Day33McpClient mcp;
    private final Day26LocalLlmClient localLlm;
    private final Day26Properties localProperties;

    public Day33SupportService(Day33Properties properties, Day33SupportIndex index, Day33CrmStore crm,
                               Day33McpClient mcp, Day26LocalLlmClient localLlm,
                               Day26Properties localProperties) {
        this.properties = properties;
        this.index = index;
        this.crm = crm;
        this.mcp = mcp;
        this.localLlm = localLlm;
        this.localProperties = localProperties;
    }

    public Day33HealthResponse health() {
        Day33SupportIndex.Snapshot snapshot = index.snapshot();
        List<String> documents = snapshot.documents().stream().map(Day21Document::fileName).toList();
        Day33McpStatus mcpStatus = mcpStatus();
        Day33ModelStatus model = modelStatus();
        String status = documents.isEmpty() ? "degraded" : "ok";
        return new Day33HealthResponse(status, endpoint(), properties.kbDir(), properties.crmDir(),
                documents, snapshot.chunks().size(), snapshot.corpusChars(),
                crm.users().size(), crm.tickets().size(), mcpStatus, model);
    }

    public List<Day33DocumentInfo> kb() {
        Day33SupportIndex.Snapshot snapshot = index.snapshot();
        Map<String, Integer> chunkCounts = new HashMap<>();
        for (Day21Chunk chunk : snapshot.chunks()) {
            chunkCounts.merge(chunk.source(), 1, Integer::sum);
        }
        return snapshot.documents().stream()
                .map(document -> new Day33DocumentInfo(
                        document.source(), document.fileName(), document.title(), document.type(),
                        document.text().length(), chunkCounts.getOrDefault(document.source(), 0)))
                .toList();
    }

    public List<Day33UserInfo> users() {
        return crm.users().stream()
                .map(user -> new Day33UserInfo(user.id(), user.name(), user.email(), user.plan(),
                        user.company(), user.since(), crm.userTickets(user.id()).size()))
                .toList();
    }

    public List<Day33TicketInfo> tickets() {
        return crm.tickets().stream()
                .map(ticket -> new Day33TicketInfo(ticket.id(), ticket.userId(), ticket.subject(),
                        ticket.status(), ticket.priority(), ticket.product(), ticket.updatedAt(),
                        ticket.tags()))
                .toList();
    }

    public Day33ToolsResponse tools() {
        try {
            Day33Connection connection = mcp.connect();
            List<Day33ToolInfo> list = mcp.listTools(connection);
            return new Day33ToolsResponse(true, endpoint(), list.size(), list, "");
        } catch (Day33McpException ex) {
            return new Day33ToolsResponse(false, endpoint(), 0, List.of(), ex.getMessage());
        }
    }

    public Day33CrmResponse crm(String tool, Map<String, Object> arguments) {
        String name = normalizeTool(tool);
        try {
            Day33Connection connection = mcp.connect();
            Day33ToolResult result = mcp.callTool(connection, name,
                    arguments == null ? Map.of() : arguments);
            return new Day33CrmResponse(name, !result.isError(), result.content());
        } catch (Day33McpException ex) {
            throw new Day33SupportException(ex.getMessage(), ex);
        }
    }

    public Day33SupportResponse ask(String question, String ticketId, String userId) {
        String questionText = Day28RagService.requireQuestion(question);
        String ticket = blankToNull(ticketId);
        String user = blankToNull(userId);
        Context context = mcpContext(ticket, user);
        List<Day21SearchHit> hits = index.search(questionText, properties.topK());
        String prompt = prompt(questionText, context, hits);
        String userName = context.userId() == null ? ""
                : crm.user(context.userId()).map(Day33User::name).orElse("");
        String ticketSubject = ticket == null ? ""
                : crm.ticket(ticket).map(Day33Ticket::subject).orElse("");
        long started = System.nanoTime();
        try {
            List<Day26ChatMessage> messages = List.of(
                    new Day26ChatMessage("system", SYSTEM),
                    new Day26ChatMessage("user", prompt));
            Day26Answer answer = localLlm.chat(messages,
                    new Day26ChatOptions(properties.temperature(), properties.answerMaxTokens(), null));
            return response(questionText, ticket, context, userName, ticketSubject,
                    answer.reply(), false, answer.model(), hits,
                    answer.latencyMs(), answer.promptTokens(), answer.outputTokens(),
                    answer.tokensPerSecond(), "");
        } catch (Day26LlmException ex) {
            long elapsed = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            log.info("День 33: локальная LLM недоступна, ответ собирается из базы знаний: {}",
                    ex.getMessage());
            return response(questionText, ticket, context, userName, ticketSubject,
                    fallbackAnswer(questionText, context, hits), true, localProperties.model(), hits,
                    elapsed, 0, 0, 0.0, ex.getMessage());
        }
    }

    private Day33SupportResponse response(String question, String ticket, Context context,
                                          String userName, String ticketSubject, String answer,
                                          boolean fallback, String model, List<Day21SearchHit> hits,
                                          long latencyMs, int promptTokens, int outputTokens,
                                          double tokensPerSecond, String note) {
        return new Day33SupportResponse(question, ticket == null ? "" : ticket,
                context.userId() == null ? "" : context.userId(), userName, ticketSubject,
                answer, fallback, model, context.userText(), context.ticketText(),
                context.available(), context.tools(), hits.size(), hits,
                Day28RagService.sources(hits), grounding(answer, hits, question),
                latencyMs, promptTokens, outputTokens, tokensPerSecond, note);
    }

    public String prompt(String question, Context context, List<Day21SearchHit> hits) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Вопрос пользователя: «").append(question).append("»\n\n");
        if (context.available()) {
            if (!context.ticketText().isEmpty()) {
                prompt.append("Контекст обращения из CRM (получен по MCP):\n")
                        .append(context.ticketText()).append("\n\n");
            }
            if (!context.userText().isEmpty()) {
                prompt.append("Контекст пользователя из CRM (получен по MCP):\n")
                        .append(context.userText()).append("\n\n");
            }
            if (context.ticketText().isEmpty() && context.userText().isEmpty()) {
                prompt.append("CRM доступна, но обращение или пользователь не указаны.\n\n");
            }
        } else {
            prompt.append("CRM недоступна: ").append(context.reason()).append('\n').append('\n');
        }
        if (hits.isEmpty()) {
            prompt.append("По запросу в базе знаний (FAQ и документация) ничего не найдено.\n");
        } else {
            prompt.append("База знаний продукта (FAQ и документация, результат поиска):\n");
            for (Day21SearchHit hit : hits) {
                prompt.append("\n[Источник: ").append(hit.chunk().source())
                        .append(", раздел: «").append(Day28RagService.section(hit))
                        .append("», чанк: ").append(hit.chunk().chunkId()).append("]\n")
                        .append(hit.chunk().text()).append('\n');
            }
        }
        prompt.append("\nПравила ответа:\n")
                .append("- сначала ответь по существу вопроса пользователя;\n")
                .append("- учитывай данные обращения и тариф пользователя из CRM;\n")
                .append("- для каждого факта укажи источник (файл) и раздел;\n")
                .append("- если данных нет — так и скажи, не выдумывай.\n");
        return prompt.toString();
    }

    public String fallbackAnswer(String question, Context context, List<Day21SearchHit> hits) {
        StringBuilder text = new StringBuilder();
        text.append("Запрос: «").append(question).append("»\n");
        text.append("Ответ собран из базы знаний (локальная LLM недоступна):\n\n");
        if (context.available() && !context.ticketText().isEmpty()) {
            text.append("Контекст обращения:\n").append(context.ticketText()).append("\n\n");
        }
        if (context.available() && !context.userText().isEmpty()) {
            text.append("Контекст пользователя:\n").append(context.userText()).append("\n\n");
        }
        if (hits.isEmpty()) {
            text.append("По запросу в базе знаний ничего не найдено.");
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

    private Context mcpContext(String ticket, String user) {
        try {
            Day33Connection connection = mcp.connect();
            List<Day33ToolInfo> tools = mcp.listTools(connection);
            String effectiveUser = user;
            String ticketText = "";
            if (ticket != null) {
                Day33ToolResult result = mcp.callTool(connection, "crm_ticket", Map.of("ticketId", ticket));
                ticketText = result.content();
                if (effectiveUser == null) {
                    effectiveUser = crm.ticket(ticket).map(Day33Ticket::userId).orElse(null);
                }
            }
            String userText = "";
            if (effectiveUser != null) {
                Day33ToolResult result = mcp.callTool(connection, "crm_user",
                        Map.of("userId", effectiveUser));
                userText = result.content();
                Day33ToolResult ticketList = mcp.callTool(connection, "crm_user_tickets",
                        Map.of("userId", effectiveUser));
                if (!ticketList.content().isBlank()) {
                    userText = userText + "\n" + ticketList.content();
                }
            }
            return new Context(true, tools.size(), userText, ticketText, effectiveUser, "");
        } catch (Day33McpException ex) {
            return new Context(false, 0, "", "", user, ex.getMessage());
        }
    }

    private Day33McpStatus mcpStatus() {
        try {
            Day33Connection connection = mcp.connect();
            List<Day33ToolInfo> list = mcp.listTools(connection);
            return new Day33McpStatus(true, endpoint(), list.size(), "");
        } catch (Day33McpException ex) {
            return new Day33McpStatus(false, endpoint(), 0, ex.getMessage());
        }
    }

    private Day33ModelStatus modelStatus() {
        try {
            String version = localLlm.version();
            boolean installed = localLlm.models().stream()
                    .anyMatch(model -> model.name() != null
                            && model.name().equalsIgnoreCase(localProperties.model()));
            return new Day33ModelStatus(localProperties.endpoint(), localProperties.model(),
                    true, version, installed, "");
        } catch (Day26LlmException ex) {
            return new Day33ModelStatus(localProperties.endpoint(), localProperties.model(),
                    false, "", false, ex.getMessage());
        } catch (RuntimeException ex) {
            return new Day33ModelStatus(localProperties.endpoint(), localProperties.model(),
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
        if (!name.isEmpty() && !name.startsWith("crm_")) {
            name = "crm_" + name;
        }
        if (!TOOLS.contains(name)) {
            throw new IllegalArgumentException("Неизвестный инструмент CRM: " + tool
                    + ". Доступны: " + String.join(", ", TOOLS));
        }
        return name;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    public record Context(boolean available, int tools, String userText, String ticketText,
                          String userId, String reason) {
    }
}
