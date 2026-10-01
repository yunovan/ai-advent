package com.yunovan.aiadvent.day24;

import com.yunovan.aiadvent.day21.Day21HealthResponse;
import com.yunovan.aiadvent.day21.Day21IndexFacade;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day23.Day23QueryRewriter;
import com.yunovan.aiadvent.day23.Day23ReRanker;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmReply;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day24Service {

    private static final Logger log = LoggerFactory.getLogger(Day24Service.class);

    private static final String SYSTEM = "Ты — ассистент проекта AI Advent. Отвечай по-русски, "
            + "опираясь ТОЛЬКО на контекст документов. В ответе ОБЯЗАТЕЛЬНО процитируй фрагменты "
            + "контекста (дословно, в кавычках) и укажи источник с разделом и номером чанка для "
            + "каждого факта. Если в контексте недостаточно информации — честно ответь «Не знаю» "
            + "и попроси пользователя уточнить вопрос. Не выдумывай факты, которых нет в контексте.";

    private static final String UNKNOWN_ANSWER = "Не знаю. В найденном контексте недостаточно "
            + "информации, чтобы ответить на этот вопрос. Уточните, пожалуйста, формулировку — "
            + "или добавьте документы по теме в корпус.";

    private final Day21IndexFacade facade;
    private final Day24Properties properties;
    private final LlmClient llm;
    private final DecimalFormat percent;

    public Day24Service(Day21IndexFacade facade, Day24Properties properties, LlmClient llm) {
        this.facade = facade;
        this.properties = properties;
        this.llm = llm;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        this.percent = new DecimalFormat("0.0", symbols);
    }

    public Day24HealthResponse health() {
        Day21HealthResponse base = facade.health();
        List<String> weak = Day24ControlQuestions.WEAK.stream()
                .map(Day24WeakQuestion::question).toList();
        return new Day24HealthResponse(base.documents(), base.corpusChars(), base.pagesEstimate(),
                properties.normalizedStrategy(), properties.topKBefore(), properties.topKAfter(),
                properties.threshold(), properties.unknownThreshold(), properties.rewrite(),
                properties.quotesPerSource(), properties.quoteMinChars(),
                properties.supportThreshold(), properties.answerMaxTokens(),
                weak, base.strategies().stream().map(s -> s.name()).toList());
    }

    public List<Day24ControlQuestion> questions() {
        return Day24ControlQuestions.ALL;
    }

    public Day24GroundedResponse answer(String question) {
        String questionText = question == null ? "" : question.trim();
        if (questionText.isBlank()) {
            throw new IllegalArgumentException("Вопрос не может быть пустым");
        }
        Pipeline pipeline = retrieve(questionText);
        double bestScore = pipeline.hits().isEmpty() ? 0.0 : pipeline.hits().get(0).score();
        boolean unknown = unknown(pipeline, bestScore);
        List<Day24Source> sources = sources(pipeline.hits());
        List<Day24Quote> quotes = unknown
                ? List.of() : Day24QuoteEngine.extract(pipeline.hits(), pipeline.matchedQuery(), properties);
        String answerText;
        boolean fallback;
        if (unknown) {
            answerText = UNKNOWN_ANSWER;
            fallback = true;
        } else {
            String content = complete(prompt(pipeline, quotes, properties), SYSTEM);
            if (content != null) {
                answerText = content;
                fallback = false;
            } else {
                answerText = groundedAnswer(quotes);
                fallback = true;
            }
        }
        double support = 0;
        boolean supported = false;
        if (!unknown) {
            support = 100.0 * Day24QuoteEngine.supportCoverage(answerText, quotes);
            supported = support / 100.0 >= properties.supportThreshold();
        }
        return new Day24GroundedResponse(questionText, pipeline.matchedQuery(),
                pipeline.rewritten(), pipeline.candidatesBefore(), pipeline.filteredOut(),
                sources, quotes, answerText, bestScore, support, supported, unknown, fallback);
    }

    public Day24EvalResponse evaluate() {
        List<Day24EvalItem> items = new ArrayList<>();
        for (Day24ControlQuestion question : Day24ControlQuestions.ALL) {
            Day24GroundedResponse response = answer(question.question());
            boolean hasSources = !response.sources().isEmpty();
            boolean hasQuotes = !response.quotes().isEmpty();
            List<String> sources = response.sources().stream()
                    .map(Day24Source::source).distinct().toList();
            items.add(new Day24EvalItem(question.id(), question.question(),
                    hasSources, response.sources().size(), hasQuotes, response.quotes().size(),
                    response.supported(), response.supportCoveragePercent(), response.unknown(),
                    sources));
        }
        int sourcesPresent = (int) items.stream().filter(Day24EvalItem::hasSources).count();
        int quotesPresent = (int) items.stream().filter(Day24EvalItem::hasQuotes).count();
        int supported = (int) items.stream().filter(Day24EvalItem::supported).count();
        double avgSupport = items.stream().mapToDouble(Day24EvalItem::supportCoveragePercent)
                .average().orElse(0);
        List<Day24EvalWeakItem> weakItems = new ArrayList<>();
        for (Day24WeakQuestion weak : Day24ControlQuestions.WEAK) {
            Day24GroundedResponse response = answer(weak.question());
            List<String> sources = response.sources().stream()
                    .map(Day24Source::source).distinct().toList();
            weakItems.add(new Day24EvalWeakItem(weak.id(), weak.question(), response.unknown(),
                    response.bestScore(), sources));
        }
        int unknownTriggered = (int) weakItems.stream().filter(Day24EvalWeakItem::unknown).count();
        String verdict = verdict(items.size(), sourcesPresent, quotesPresent, supported,
                avgSupport, weakItems.size(), unknownTriggered);
        return new Day24EvalResponse(items.size(), sourcesPresent, quotesPresent, supported,
                avgSupport, weakItems.size(), unknownTriggered, verdict, items, weakItems);
    }

    private Pipeline retrieve(String question) {
        Day23QueryRewriter.Rewrite rewrite = properties.rewrite()
                ? Day23QueryRewriter.rewrite(question)
                : new Day23QueryRewriter.Rewrite(question, Day23QueryRewriter.normalize(question),
                List.of());
        String matched = rewrite.rewritten();
        String strategy = properties.normalizedStrategy();
        Map<String, Double> dense = new LinkedHashMap<>();
        for (Day21SearchHit hit : facade.search(strategy, matched, properties.topKBefore()).hits()) {
            dense.put(hit.chunk().chunkId(), hit.score());
        }
        List<Day21SearchHit> ranked = Day23ReRanker.rank(matched, facade.chunks(strategy), dense);
        List<Day21SearchHit> considered = ranked.stream()
                .limit(properties.topKBefore()).toList();
        List<Day21SearchHit> filtered = considered.stream()
                .filter(hit -> hit.score() >= properties.threshold()).toList();
        int filteredOut = considered.size() - filtered.size();
        List<Day21SearchHit> hits = filtered.stream()
                .limit(properties.topKAfter()).toList();
        return new Pipeline(question, matched, Day23QueryRewriter.rewriteApplied(rewrite),
                considered.size(), filteredOut, hits);
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
            Day21ChunkInfo info = chunkInfo(hit);
            result.add(new Day24Source(info.source(), info.section(), info.chunkId(),
                    hit.score(), hit.snippet()));
        }
        return result;
    }

    private static String prompt(Pipeline pipeline, List<Day24Quote> quotes,
                                 Day24Properties properties) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Вопрос пользователя: «").append(pipeline.question()).append("»\n\n");
        if (pipeline.rewritten()) {
            prompt.append("Перед поиском запрос был переписан: «")
                    .append(pipeline.matchedQuery()).append("»\n\n");
        }
        if (pipeline.filteredOut() > 0) {
            prompt.append("Из ").append(pipeline.candidatesBefore())
                    .append(" кандидатов ").append(pipeline.filteredOut())
                    .append(" отсеяны как нерелевантные (score ниже порога ").append(percent(properties))
                    .append(").\n");
        }
        prompt.append("\nКонтекст документов (результат поиска по локальному индексу):\n");
        for (Day21SearchHit hit : pipeline.hits()) {
            Day21ChunkInfo info = chunkInfo(hit);
            prompt.append("\n[Источник: ").append(info.source())
                    .append(", раздел: «").append(info.section())
                    .append("», чанк: ").append(info.chunkId()).append("]\n")
                    .append(hit.chunk().text()).append('\n');
        }
        prompt.append("\nПравила ответа:\n")
                .append("- верни ответ и обязательно приведи дословные цитаты из контекста;\n")
                .append("- для каждой цитаты укажи источник, раздел и номер чанка;\n")
                .append("- если цитат, подтверждающих ответ, нет — ответь «Не знаю» и попроси уточнить.\n");
        return prompt.toString();
    }

    private static String groundedAnswer(List<Day24Quote> quotes) {
        if (quotes.isEmpty()) {
            return UNKNOWN_ANSWER;
        }
        StringBuilder text = new StringBuilder();
        text.append("В документах говорится: «").append(quotes.get(0).text()).append("»");
        for (int i = 1; i < quotes.size(); i++) {
            Day24Quote quote = quotes.get(i);
            text.append(" Ещё в источнике «").append(quote.source())
                    .append("» (раздел «").append(quote.section()).append("»): «")
                    .append(quote.text()).append("»");
        }
        text.append(" Ответ полностью опирается на эти цитаты.");
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
            log.info("День 24: LLM недоступен, соберём ответ из цитат индекса: {}", ex.getMessage());
        }
        return null;
    }

    private String verdict(int known, int sourcesPresent, int quotesPresent, int supported,
                           double avgSupport, int weak, int unknownTriggered) {
        String all = known == 0 ? "?" : String.valueOf(known);
        return "Комплаентность на " + all + " вопросах: источники " + sourcesPresent + "/"
                + all + ", цитаты " + quotesPresent + "/" + all
                + ", смысл ответа подтверждён цитатами " + supported + "/" + all
                + " (средняя поддержка " + percent.format(avgSupport) + "%). "
                + "Анти-галлюцинация: режим «не знаю» сработал на " + unknownTriggered + "/"
                + weak + " слабых вопросах.";
    }

    private static String percent(Day24Properties properties) {
        return String.format(Locale.ROOT, "%.2f", properties.threshold());
    }

    private static Day21ChunkInfo chunkInfo(Day21SearchHit hit) {
        String section = hit.chunk().section() == null || hit.chunk().section().isEmpty()
                ? hit.chunk().title() : hit.chunk().section();
        return new Day21ChunkInfo(hit.chunk().source(), section, hit.chunk().chunkId());
    }

    private record Day21ChunkInfo(String source, String section, String chunkId) {
    }

    private record Pipeline(String question, String matchedQuery, boolean rewritten,
                            int candidatesBefore, int filteredOut, List<Day21SearchHit> hits) {
    }
}