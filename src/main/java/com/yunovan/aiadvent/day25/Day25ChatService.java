package com.yunovan.aiadvent.day25;

import com.yunovan.aiadvent.day21.Day21HealthResponse;
import com.yunovan.aiadvent.day21.Day21IndexFacade;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day23.Day23QueryRewriter;
import com.yunovan.aiadvent.day23.Day23ReRanker;
import com.yunovan.aiadvent.day24.Day24QuoteEngine;
import com.yunovan.aiadvent.day24.Day24Quote;
import com.yunovan.aiadvent.day24.Day24Source;
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
public class Day25ChatService {

    private static final Logger log = LoggerFactory.getLogger(Day25ChatService.class);

    private static final int MAX_CLARIFICATIONS = 6;
    private static final int MAX_CONSTRAINTS = 6;
    private static final int HISTORY_PROMPT_MESSAGES = 6;

    private static final String SYSTEM = "Ты — ассистент проекта AI Advent в мини-чате с RAG. "
            + "Отвечай по-русски, опираясь ТОЛЬКО на найденный контекст документов и на цель "
            + "диалога, которую помнит ассистент. Учитывай уточнения пользователя и зафиксированные "
            + "ограничения. Для каждого факта укажи источник, раздел и номер чанка. Если в контексте "
            + "недостаточно информации — честно ответь «Не знаю» и попроси уточнить. Не выдумывай.";

    private static final String UNKNOWN_ANSWER = "Не знаю. В найденном контексте недостаточно "
            + "информации, чтобы ответить по этой теме. Уточните, пожалуйста, вопрос — или добавьте "
            + "документы по теме в корпус.";

    private final Day21IndexFacade facade;
    private final Day25Properties properties;
    private final LlmClient llm;
    private final Day25ChatSessionStore store;
    private final com.yunovan.aiadvent.day24.Day24Properties quoteProperties;

    public Day25ChatService(Day21IndexFacade facade, Day25Properties properties, LlmClient llm,
                            Day25ChatSessionStore store) {
        this.facade = facade;
        this.properties = properties;
        this.llm = llm;
        this.store = store;
        this.quoteProperties = new com.yunovan.aiadvent.day24.Day24Properties(
                properties.strategy(), properties.topKBefore(), properties.topKAfter(),
                properties.threshold(), properties.unknownThreshold(), properties.rewrite(),
                properties.quotesPerSource(), properties.quoteMinChars(),
                properties.supportThreshold(), properties.answerMaxTokens());
    }

    public Day25HealthResponse health() {
        Day21HealthResponse base = facade.health();
        return new Day25HealthResponse(base.documents(), base.corpusChars(), base.pagesEstimate(),
                properties.normalizedStrategy(), properties.topKBefore(), properties.topKAfter(),
                properties.threshold(), properties.unknownThreshold(), properties.rewrite(),
                properties.quotesPerSource(), properties.quoteMinChars(),
                properties.supportThreshold(), properties.answerMaxTokens(),
                properties.historyLimit(), properties.maxSessions(), properties.memoryTermsLimit(),
                store.size(),
                Day25Scenarios.ALL.stream().map(Day25Scenario::title).toList(),
                base.strategies().stream().map(s -> s.name()).toList());
    }

    public List<Day25Scenario> scenarios() {
        return Day25Scenarios.ALL;
    }

    public List<Day25SessionView> sessions() {
        List<Day25SessionView> views = new ArrayList<>();
        for (Day25ChatSessionStore.Session session : store.all()) {
            views.add(view(session));
        }
        return views;
    }

    public Day25SessionView memory(String sessionId) {
        Day25ChatSessionStore.Session session = store.get(normalize(sessionId));
        return session == null ? new Day25SessionView(normalize(sessionId), 0, 0,
                null, null, 0, 0, 0) : view(session);
    }

    public List<Day25Message> history(String sessionId) {
        Day25ChatSessionStore.Session session = store.get(normalize(sessionId));
        return session == null ? List.of() : session.messages();
    }

    public Day25TaskMemory taskMemory(String sessionId) {
        Day25ChatSessionStore.Session session = store.get(normalize(sessionId));
        return session == null ? new Day25TaskMemory(null, 0, 0, List.of(), List.of(), List.of())
                : session.memory();
    }

    public void reset(String sessionId) {
        store.reset(normalize(sessionId));
    }

    public Day25ChatTurn chat(String rawSessionId, String rawMessage) {
        String sessionId = normalize(rawSessionId);
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (message.isBlank()) {
            throw new IllegalArgumentException("Сообщение не может быть пустым");
        }
        Day25ChatSessionStore.Session session = store.get(sessionId);
        int turn = session == null ? 1 : session.messages().size() / 2 + 1;
        Day25TaskMemory memory = remember(session == null
                ? new Day25TaskMemory(null, turn, 0, List.of(), List.of(), List.of())
                : session.memory(), message, turn);

        Pipeline pipeline = retrieve(message, memory);
        double bestScore = pipeline.hits().isEmpty() ? 0.0 : pipeline.hits().get(0).score();
        boolean unknown = unknown(pipeline, bestScore);
        List<Day24Source> sources = sources(pipeline.hits());
        List<Day24Quote> quotes = unknown ? List.of()
                : Day24QuoteEngine.extract(pipeline.hits(), pipeline.matchedQuery(), quoteProperties);
        String reply;
        boolean fallback;
        if (unknown) {
            reply = UNKNOWN_ANSWER;
            fallback = true;
        } else {
            String content = complete(prompt(message, memory, session, pipeline, quotes), SYSTEM);
            if (content != null) {
                reply = content;
                fallback = false;
            } else {
                reply = groundedAnswer(quotes, memory);
                fallback = true;
            }
        }
        double support = 0;
        boolean supported = false;
        if (!unknown) {
            support = 100.0 * Day24QuoteEngine.supportCoverage(reply, quotes);
            supported = support / 100.0 >= properties.supportThreshold();
        }

        List<Day25Message> messages = new ArrayList<>(session == null ? List.of() : session.messages());
        messages.add(new Day25Message("user", message, turn));
        messages.add(new Day25Message("assistant", reply, turn));
        messages = trim(messages);
        store.save(new Day25ChatSessionStore.Session(sessionId, messages, memory));

        return new Day25ChatTurn(sessionId, turn, message, reply, pipeline.matchedQuery(),
                pipeline.rewritten(), pipeline.candidatesBefore(), pipeline.filteredOut(), bestScore,
                sources, quotes, support, supported, unknown, fallback, memory, messages,
                messages.size());
    }

    public Day25EvalResponse evaluate() {
        List<Day25ScenarioResult> results = new ArrayList<>();
        for (Day25Scenario scenario : Day25Scenarios.ALL) {
            String sessionId = "scenario-" + scenario.id();
            store.reset(sessionId);
            List<Day25ScenarioTurn> details = new ArrayList<>();
            List<Double> supports = new ArrayList<>();
            for (int i = 0; i < scenario.messages().size(); i++) {
                Day25ChatTurn chatTurn = chat(sessionId, scenario.messages().get(i));
                boolean goalRetained = Day25MemoryExtractor.retainsGoal(chatTurn.memory(),
                        scenario.expectedGoalTerms());
                boolean constraintsKept = Day25MemoryExtractor.keepsConstraints(chatTurn.memory(),
                        scenario.expectedConstraints());
                details.add(new Day25ScenarioTurn(chatTurn.turn(), chatTurn.userMessage(),
                        !chatTurn.sources().isEmpty(), chatTurn.sources().size(),
                        chatTurn.quotes().size(), chatTurn.supported(), chatTurn.unknown(),
                        goalRetained, constraintsKept, chatTurn.bestScore(),
                        chatTurn.sources().stream().map(Day24Source::source).distinct().toList()));
                supports.add(chatTurn.supportCoveragePercent());
            }
            Day25TaskMemory memory = taskMemory(sessionId);
            store.reset(sessionId);
            int withSources = (int) details.stream().filter(Day25ScenarioTurn::hasSources).count();
            int withQuotes = (int) details.stream().filter(t -> t.quotesCount() > 0).count();
            int supported = (int) details.stream().filter(Day25ScenarioTurn::supported).count();
            int goalRetained = (int) details.stream().filter(Day25ScenarioTurn::goalRetained).count();
            int constraintsKept = (int) details.stream().filter(Day25ScenarioTurn::constraintsKept).count();
            int unknown = (int) details.stream().filter(Day25ScenarioTurn::unknown).count();
            double avgSupport = supports.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            results.add(new Day25ScenarioResult(scenario.id(), scenario.title(), scenario.goal(),
                    details.size(), withSources, withQuotes, supported, goalRetained, constraintsKept,
                    avgSupport, unknown, memory, details));
        }
        int totalTurns = results.stream().mapToInt(Day25ScenarioResult::turns).sum();
        int withSources = results.stream().mapToInt(Day25ScenarioResult::turnsWithSources).sum();
        int withQuotes = results.stream().mapToInt(Day25ScenarioResult::quotesTurns).sum();
        int supported = results.stream().mapToInt(Day25ScenarioResult::supportedTurns).sum();
        int goalRetained = results.stream().mapToInt(Day25ScenarioResult::goalRetainedTurns).sum();
        int constraintsKept = results.stream().mapToInt(Day25ScenarioResult::constraintsKeptTurns).sum();
        int unknown = results.stream().mapToInt(Day25ScenarioResult::unknownTurns).sum();
        double avgSupport = results.stream().mapToDouble(Day25ScenarioResult::avgSupportPercent)
                .average().orElse(0);
        String verdict = verdict(results.size(), totalTurns, withSources, goalRetained, constraintsKept,
                avgSupport);
        return new Day25EvalResponse(results.size(), totalTurns, withSources, withQuotes, supported,
                goalRetained, constraintsKept, unknown, avgSupport, verdict, results);
    }

    private Day25TaskMemory remember(Day25TaskMemory memory, String message, int turn) {
        Day25TaskMemory result = memory.advanced(turn);
        String goal = Day25MemoryExtractor.goal(message);
        if (goal != null) {
            result = result.withGoal(goal, turn);
        }
        for (String constraint : Day25MemoryExtractor.constraints(message)) {
            result = result.withConstraint(constraint, MAX_CONSTRAINTS);
        }
        result = result.withTerms(
                Day25MemoryExtractor.terms(message, properties.memoryTermsLimit() * 2),
                properties.memoryTermsLimit());
        if (turn > 1 && goal == null && message.length() <= 160) {
            result = result.withClarification(message, MAX_CLARIFICATIONS);
        }
        return result;
    }

    private Pipeline retrieve(String message, Day25TaskMemory memory) {
        String context = memoryQuery(memory);
        String query = context.isBlank() ? message : message + " " + context;
        Day23QueryRewriter.Rewrite rewrite = properties.rewrite()
                ? Day23QueryRewriter.rewrite(query)
                : new Day23QueryRewriter.Rewrite(query, Day23QueryRewriter.normalize(query), List.of());
        String matched = rewrite.rewritten();
        String strategy = properties.normalizedStrategy();
        Map<String, Double> dense = new LinkedHashMap<>();
        for (Day21SearchHit hit : facade.search(strategy, matched, properties.topKBefore()).hits()) {
            dense.put(hit.chunk().chunkId(), hit.score());
        }
        List<Day21SearchHit> ranked = Day23ReRanker.rank(matched, facade.chunks(strategy), dense);
        List<Day21SearchHit> considered = ranked.stream().limit(properties.topKBefore()).toList();
        List<Day21SearchHit> filtered = considered.stream()
                .filter(hit -> hit.score() >= properties.threshold()).toList();
        int filteredOut = considered.size() - filtered.size();
        List<Day21SearchHit> hits = filtered.stream().limit(properties.topKAfter()).toList();
        return new Pipeline(message, matched, Day23QueryRewriter.rewriteApplied(rewrite),
                considered.size(), filteredOut, hits);
    }

    private String memoryQuery(Day25TaskMemory memory) {
        List<String> parts = new ArrayList<>();
        if (memory.goal() != null && !memory.goal().isBlank()) {
            parts.add(memory.goal());
        }
        parts.addAll(memory.terms());
        return String.join(" ", parts);
    }

    private boolean unknown(Pipeline pipeline, double bestScore) {
        if (pipeline.hits().isEmpty()) {
            return true;
        }
        if (bestScore < properties.unknownThreshold()) {
            return true;
        }
        return Day24QuoteEngine.lexicalEvidence(pipeline.hits(), pipeline.matchedQuery()) == 0;
    }

    private List<Day24Source> sources(List<Day21SearchHit> hits) {
        List<Day24Source> result = new ArrayList<>();
        Map<String, Day21SearchHit> byChunk = new LinkedHashMap<>();
        for (Day21SearchHit hit : hits) {
            byChunk.putIfAbsent(hit.chunk().chunkId(), hit);
        }
        for (Day21SearchHit hit : byChunk.values()) {
            result.add(new Day24Source(hit.chunk().source(), section(hit), hit.chunk().chunkId(),
                    hit.score(), hit.snippet()));
        }
        return result;
    }

    private String prompt(String message, Day25TaskMemory memory,
                          Day25ChatSessionStore.Session session, Pipeline pipeline,
                          List<Day24Quote> quotes) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Цель диалога, которую помнит ассистент: ")
                .append(memory.goal() == null ? "пока не задана" : memory.goal()).append('\n');
        if (!memory.constraints().isEmpty()) {
            prompt.append("Ограничения и термины, зафиксированные пользователем: ")
                    .append(String.join("; ", memory.constraints())).append('\n');
        }
        if (!memory.clarifications().isEmpty()) {
            prompt.append("Что пользователь уже уточнил: ")
                    .append(String.join("; ", memory.clarifications())).append('\n');
        }
        prompt.append("\nНовое сообщение пользователя: «").append(message).append("»\n\n");
        List<Day25Message> history = session == null ? List.of() : session.messages();
        if (!history.isEmpty()) {
            prompt.append("История диалога (последние сообщения):\n");
            for (Day25Message item : tail(history, HISTORY_PROMPT_MESSAGES)) {
                prompt.append(item.role().equals("user") ? "  пользователь: " : "  ассистент: ")
                        .append(item.text()).append('\n');
            }
            prompt.append('\n');
        }
        if (pipeline.rewritten()) {
            prompt.append("Запрос для поиска (с учётом памяти задачи): «")
                    .append(pipeline.matchedQuery()).append("»\n\n");
        }
        prompt.append("\nКонтекст документов (результат поиска по локальному индексу):\n");
        for (Day21SearchHit hit : pipeline.hits()) {
            prompt.append("\n[Источник: ").append(hit.chunk().source())
                    .append(", раздел: «").append(section(hit))
                    .append("», чанк: ").append(hit.chunk().chunkId()).append("]\n")
                    .append(hit.chunk().text()).append('\n');
        }
        prompt.append("\nПравила ответа:\n")
                .append("- отвечай по цели диалога, учитывай уточнения и ограничения пользователя;\n")
                .append("- приведи дословные цитаты из контекста и укажи источник, раздел и номер чанка;\n")
                .append("- если цитат, подтверждающих ответ, нет — ответь «Не знаю» и попроси уточнить.\n");
        return prompt.toString();
    }

    private String groundedAnswer(List<Day24Quote> quotes, Day25TaskMemory memory) {
        if (quotes.isEmpty()) {
            return UNKNOWN_ANSWER;
        }
        StringBuilder text = new StringBuilder();
        if (memory.goal() != null && !memory.goal().isBlank()) {
            text.append("По цели диалога («").append(memory.goal()).append("»): ");
        }
        text.append("«").append(quotes.getFirst().text()).append("»");
        for (int i = 1; i < quotes.size(); i++) {
            Day24Quote quote = quotes.get(i);
            text.append(" Ещё в источнике «").append(quote.source()).append("» (раздел «")
                    .append(quote.section()).append("»): «").append(quote.text()).append("»");
        }
        return text.toString();
    }

    private String complete(String message, String system) {
        try {
            LlmReply reply = llm.complete(new CompletionCommand(message, system,
                    properties.answerMaxTokens(), null));
            String content = reply == null ? null
                    : reply.content() == null ? null : reply.content().trim();
            if (content != null && !content.isBlank()) {
                return content;
            }
        } catch (LlmException ex) {
            log.info("День 25: LLM недоступен, соберём ответ из цитат индекса: {}", ex.getMessage());
        }
        return null;
    }

    private List<Day25Message> trim(List<Day25Message> messages) {
        if (messages.size() <= properties.historyLimit()) {
            return messages;
        }
        int from = messages.size() - properties.historyLimit();
        if (from % 2 != 0) {
            from++;
        }
        return new ArrayList<>(messages.subList(from, messages.size()));
    }

    private static List<Day25Message> tail(List<Day25Message> messages, int count) {
        if (messages.size() <= count) {
            return messages;
        }
        return messages.subList(messages.size() - count, messages.size());
    }

    private Day25SessionView view(Day25ChatSessionStore.Session session) {
        List<Day25Message> messages = session.messages();
        String last = messages.isEmpty() ? null : messages.getLast().text();
        Day25TaskMemory memory = session.memory();
        return new Day25SessionView(session.sessionId(), messages.size() / 2, messages.size(),
                memory.goal(), last, memory.userTurns(), memory.constraints().size(),
                memory.terms().size());
    }

    private String verdict(int scenarios, int turns, int withSources, int goalRetained,
                           int constraintsKept, double avgSupport) {
        return "Мини-чат на " + scenarios + " длинных сценариях, " + turns + " сообщений: "
                + "ответы с источниками " + withSources + "/" + turns + ", цель диалога удержана "
                + goalRetained + "/" + turns + ", ограничения удержаны " + constraintsKept + "/" + turns
                + " (средняя поддержка ответа цитатами " + String.format(Locale.ROOT, "%.1f", avgSupport)
                + "%).";
    }

    private static String normalize(String sessionId) {
        return sessionId == null || sessionId.isBlank() ? "default" : sessionId.trim();
    }

    private static String section(Day21SearchHit hit) {
        return hit.chunk().section() == null || hit.chunk().section().isEmpty()
                ? hit.chunk().title() : hit.chunk().section();
    }

    private record Pipeline(String question, String matchedQuery, boolean rewritten,
                            int candidatesBefore, int filteredOut, List<Day21SearchHit> hits) {
    }
}
