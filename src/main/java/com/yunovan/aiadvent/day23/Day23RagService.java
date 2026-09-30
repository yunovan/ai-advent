package com.yunovan.aiadvent.day23;

import com.yunovan.aiadvent.day21.Day21HealthResponse;
import com.yunovan.aiadvent.day21.Day21IndexFacade;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmReply;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day23RagService {

    private static final Logger log = LoggerFactory.getLogger(Day23RagService.class);

    public static final String MODE_BASE = "base";
    public static final String MODE_FILTER = "filter";
    public static final String MODE_REWRITE = "rewrite";
    public static final String MODE_FULL = "full";
    public static final String DEFAULT_MODE = MODE_FULL;

    private static final String RAG_SYSTEM =
            "Ты — ассистент проекта AI Advent. Отвечай пользователю по-русски, опираясь ТОЛЬКО "
                    + "на контекст документов. Если ответа нет в контексте — честно скажи, что в индексе "
                    + "этого нет. Для каждого факта указывай источник и раздел, откуда он взят.";

    private final Day21IndexFacade facade;
    private final Day23Properties properties;
    private final LlmClient llm;
    private final DecimalFormat percent;

    public Day23RagService(Day21IndexFacade facade, Day23Properties properties, LlmClient llm) {
        this.facade = facade;
        this.properties = properties;
        this.llm = llm;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        this.percent = new DecimalFormat("0.0", symbols);
    }

    public Day23HealthResponse health() {
        Day21HealthResponse base = facade.health();
        return new Day23HealthResponse(base.documents(), base.corpusChars(), base.pagesEstimate(),
                properties.normalizedStrategy(), properties.topKBefore(), properties.topKAfter(),
                properties.threshold(), properties.rewrite(), properties.answerMaxTokens(),
                base.strategies());
    }

    public List<Day23ControlQuestion> questions() {
        return Day23ControlQuestions.ALL;
    }

    public Day23RewriteResponse rewrite(String question) {
        String questionText = question == null ? "" : question.trim();
        if (questionText.isBlank()) {
            throw new IllegalArgumentException("Вопрос не может быть пустым");
        }
        Day23QueryRewriter.Rewrite rewrite = Day23QueryRewriter.rewrite(questionText);
        return new Day23RewriteResponse(questionText, rewrite.rewritten(),
                Day23QueryRewriter.rewriteApplied(rewrite), rewrite.expansions());
    }

    public Day23AnswerResponse answer(String question, String mode) {
        String questionText = question == null ? "" : question.trim();
        if (questionText.isBlank()) {
            throw new IllegalArgumentException("Вопрос не может быть пустым");
        }
        String usedMode = normalizeMode(mode);
        Day23Pipeline pipeline = retrieve(questionText, usedMode);
        String content = complete(ragPrompt(pipeline, properties.threshold()), RAG_SYSTEM);
        boolean fallback = content == null;
        if (fallback) {
            content = ragFallback(pipeline);
        }
        return new Day23AnswerResponse(questionText, usedMode, pipeline.matchedQuery(),
                pipeline.rewritten(), pipeline.candidatesBefore(), pipeline.filteredOut(),
                pipeline.hits(), content, fallback);
    }

    public Day23AnswerResponse ask(String prompt) {
        String promptText = prompt == null ? "" : prompt.trim();
        if (promptText.isBlank()) {
            throw new IllegalArgumentException("Вопрос не может быть пустым");
        }
        return answer(promptText, autoMode(promptText));
    }

    public Day23CompareResponse compare(String question, String firstMode, String secondMode) {
        String questionText = question == null ? "" : question.trim();
        if (questionText.isBlank()) {
            throw new IllegalArgumentException("Вопрос не может быть пустым");
        }
        Day23AnswerResponse first = answer(questionText,
                firstMode == null || firstMode.isBlank() ? MODE_BASE : firstMode);
        Day23AnswerResponse second = answer(questionText,
                secondMode == null || secondMode.isBlank() ? MODE_FULL : secondMode);
        List<String> sourcesBefore = first.retrievedHits().stream()
                .map(hit -> hit.chunk().fileName()).distinct().toList();
        List<String> sourcesAfter = second.retrievedHits().stream()
                .map(hit -> hit.chunk().fileName()).distinct().toList();
        String verdict = verdict(first, second, questionText);
        return new Day23CompareResponse(questionText, first, second,
                sourcesBefore, sourcesAfter, verdict);
    }

    public Day23EvalResponse evaluate() {
        List<Day23EvalItem> items = new ArrayList<>();
        for (Day23ControlQuestion question : Day23ControlQuestions.ALL) {
            Day23AnswerResponse base = answer(question.question(), MODE_BASE);
            Day23AnswerResponse full = answer(question.question(), MODE_FULL);
            List<String> baseSources = base.retrievedHits().stream()
                    .map(hit -> hit.chunk().fileName()).distinct().toList();
            List<String> fullSources = full.retrievedHits().stream()
                    .map(hit -> hit.chunk().fileName()).distinct().toList();
            boolean baseHit = question.expectedSources().stream().anyMatch(baseSources::contains);
            boolean fullHit = question.expectedSources().stream().anyMatch(fullSources::contains);
            double baseCoverage = 100.0 * keywordCoverage(question.expectedKeywords(), base.answer());
            double fullCoverage = 100.0 * keywordCoverage(question.expectedKeywords(), full.answer());
            items.add(new Day23EvalItem(question.id(), question.question(),
                    question.expectedKeywords(), question.expectedSources(),
                    baseSources, baseHit, baseCoverage, base.candidatesBefore(),
                    fullSources, fullHit, fullCoverage, full.filteredOut()));
        }
        int baseHits = (int) items.stream().filter(Day23EvalItem::baseHit).count();
        int fullHits = (int) items.stream().filter(Day23EvalItem::fullHit).count();
        int filteredOut = items.stream().mapToInt(Day23EvalItem::filteredOut).sum();
        double baseAvg = items.stream().mapToDouble(Day23EvalItem::baseCoveragePercent)
                .average().orElse(0);
        double fullAvg = items.stream().mapToDouble(Day23EvalItem::fullCoveragePercent)
                .average().orElse(0);
        double gap = fullAvg - baseAvg;
        int improved = (int) items.stream()
                .filter(item -> item.fullHit() && item.fullCoveragePercent() >= item.baseCoveragePercent())
                .count();
        double baseRecall = 100.0 * baseHits / items.size();
        double fullRecall = 100.0 * fullHits / items.size();
        return new Day23EvalResponse(items.size(), baseHits, fullHits, baseRecall, fullRecall,
                filteredOut, baseAvg, fullAvg, gap, improved,
                verdict(baseRecall, fullRecall, filteredOut, baseAvg, fullAvg, gap, items.size()),
                items);
    }

    private Day23Pipeline retrieve(String question, String mode) {
        boolean filterEnabled = mode.equals(MODE_FILTER) || mode.equals(MODE_FULL);
        boolean rewriteEnabled = (mode.equals(MODE_REWRITE) || mode.equals(MODE_FULL))
                && properties.rewrite();
        Day23QueryRewriter.Rewrite rewrite = rewriteEnabled
                ? Day23QueryRewriter.rewrite(question)
                : new Day23QueryRewriter.Rewrite(question, Day23QueryRewriter.normalize(question), List.of());
        String matched = rewrite.rewritten();
        String strategy = properties.normalizedStrategy();
        Map<String, Double> dense = new HashMap<>();
        for (Day21SearchHit hit : facade.search(strategy, matched, properties.topKBefore()).hits()) {
            dense.put(hit.chunk().chunkId(), hit.score());
        }
        List<Day21SearchHit> ranked = Day23ReRanker.rank(matched, facade.chunks(strategy), dense);
        List<Day21SearchHit> considered = ranked.stream()
                .limit(properties.topKBefore()).toList();
        if (!filterEnabled) {
            List<Day21SearchHit> hits = considered.stream()
                    .limit(properties.topKAfter()).toList();
            return new Day23Pipeline(question, matched, rewriteEnabled,
                    considered.size(), 0, hits);
        }
        List<Day21SearchHit> filtered = considered.stream()
                .filter(hit -> hit.score() >= properties.threshold()).toList();
        int filteredOut = considered.size() - filtered.size();
        List<Day21SearchHit> hits = filtered.stream()
                .limit(properties.topKAfter()).toList();
        return new Day23Pipeline(question, matched, rewriteEnabled,
                considered.size(), filteredOut, hits);
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
            log.info("День 23: LLM недоступен, сформируем ответ из контекста индекса: {}", ex.getMessage());
        }
        return null;
    }

    private static String ragPrompt(Day23Pipeline pipeline, double threshold) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Вопрос пользователя: «").append(pipeline.question()).append("»\n\n");
        if (pipeline.rewritten()) {
            prompt.append("Перед поиском запрос был переписан: «")
                    .append(pipeline.matchedQuery()).append("»\n\n");
        }
        if (pipeline.hits().isEmpty()) {
            prompt.append("По запросу в индексе документов ничего не найдено.\n");
            return prompt.toString();
        }
        if (pipeline.filteredOut() > 0) {
            prompt.append("Из ").append(pipeline.candidatesBefore())
                    .append(" кандидатов ").append(pipeline.filteredOut())
                    .append(" отсеяны как нерелевантные (score ниже порога ")
                    .append(String.format(Locale.ROOT, "%.2f", threshold)).append(").\n");
        }
        prompt.append("\nКонтекст документов (результат поиска по локальному индексу):\n");
        for (Day21SearchHit hit : pipeline.hits()) {
            prompt.append("\n[Источник: ").append(hit.chunk().source())
                    .append(", раздел: «").append(section(hit)).append("»")
                    .append(", чанк: ").append(hit.chunk().chunkId()).append("]\n")
                    .append(hit.chunk().text()).append('\n');
        }
        return prompt.toString();
    }

    private static String ragFallback(Day23Pipeline pipeline) {
        StringBuilder text = new StringBuilder();
        text.append("Запрос: «").append(pipeline.question()).append("»\n");
        if (pipeline.rewritten()) {
            text.append("Переписанный запрос для поиска: «").append(pipeline.matchedQuery())
                    .append("»\n");
        }
        text.append("Кандидатов до фильтрации: ").append(pipeline.candidatesBefore())
                .append(", отсеяно по порогу: ").append(pipeline.filteredOut()).append('\n');
        text.append("Ответ сформирован из контекста индекса (LLM недоступен):\n");
        if (pipeline.hits().isEmpty()) {
            text.append("По запросу в индексе — ничего не найдено.\n");
            return text.toString().trim();
        }
        for (Day21SearchHit hit : pipeline.hits()) {
            text.append("- [").append(hit.chunk().source())
                    .append(", раздел «").append(section(hit)).append("», score ")
                    .append(String.format(Locale.ROOT, "%.3f", hit.score())).append("]:\n")
                    .append(hit.chunk().text()).append("\n\n");
        }
        return text.toString().trim();
    }

    private static String section(Day21SearchHit hit) {
        return hit.chunk().section().isEmpty() ? hit.chunk().title() : hit.chunk().section();
    }

    static double keywordCoverage(List<String> keywords, String answer) {
        if (keywords == null || keywords.isEmpty() || answer == null || answer.isBlank()) {
            return 0;
        }
        String lower = answer.toLowerCase(Locale.ROOT);
        long found = keywords.stream()
                .filter(keyword -> keyword != null && !keyword.isBlank()
                        && lower.contains(keyword.toLowerCase(Locale.ROOT)))
                .count();
        return (double) found / keywords.size();
    }

    public static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return DEFAULT_MODE;
        }
        String lower = mode.trim().toLowerCase(Locale.ROOT);
        if (lower.equals(MODE_BASE) || lower.equals("bare") || lower.equals("чернов")
                || lower.equals("как есть") || lower.equals("без фильтра")) {
            return MODE_BASE;
        }
        if (lower.equals(MODE_FILTER) || lower.contains("фильтр")) {
            return MODE_FILTER;
        }
        if (lower.equals(MODE_REWRITE) || lower.equals("rewriter") || lower.contains("перепис")) {
            return MODE_REWRITE;
        }
        if (lower.equals(MODE_FULL) || lower.contains("полн") || lower.contains("все")) {
            return MODE_FULL;
        }
        return DEFAULT_MODE;
    }

    private static String autoMode(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        if (lower.contains("без фильтра") || lower.contains("без реранк")
                || lower.contains("как есть") || lower.contains("base")) {
            return MODE_BASE;
        }
        if (lower.contains("перепиши") || lower.contains("rewrite") || lower.contains("rewriter")) {
            return MODE_REWRITE;
        }
        if (lower.contains("только фильтр") || lower.contains("с фильтром")) {
            return MODE_FILTER;
        }
        return DEFAULT_MODE;
    }

    private String verdict(Day23AnswerResponse first, Day23AnswerResponse second, String questionText) {
        StringBuilder text = new StringBuilder();
        text.append("Вопрос: «").append(questionText).append("». ");
        text.append("Режим «").append(second.mode()).append("» (").append(first.mode())
                .append(" vs ").append(second.mode()).append("): ");
        if (second.rewritten() && !first.rewritten()) {
            text.append("запрос переписан: «").append(second.matchedQuery()).append("»; ");
        }
        if (second.filteredOut() > 0 && first.filteredOut() == 0) {
            text.append("из ").append(second.candidatesBefore())
                    .append(" кандидатов отсеяно по порогу ").append(second.filteredOut())
                    .append(" нерелевантных; ");
        }
        List<String> sources = second.retrievedHits().stream()
                .map(hit -> hit.chunk().fileName()).distinct().toList();
        if (!sources.isEmpty()) {
            text.append("в контекст попало ").append(sources.size())
                    .append(" источников (").append(String.join(", ", sources)).append(").");
        } else {
            text.append("релевантных источников после фильтрации не осталось.");
        }
        return text.toString();
    }

    private String verdict(double baseRecall, double fullRecall, int filteredOut,
                           double baseAvg, double fullAvg, double gap, int total) {
        String filterPart = filteredOut > 0
                ? "фильтр отсеял " + filteredOut + " нерелевантных кандидатов на "
                + total + " вопросах; " : "фильтр ничего не отсеял (порог слишком низкий); ";
        String recallPart = fullRecall >= baseRecall
                ? "recall не упал и остался на уровне " + percent.format(fullRecall)
                + "% (base " + percent.format(baseRecall) + "%)"
                : "recall в полном режиме ниже, чем в base (" + percent.format(fullRecall)
                + "% против " + percent.format(baseRecall) + "%) — порог стоит снизить";
        String coveragePart = gap >= 0
                ? "среднее покрытие ключевых слов выросло с " + percent.format(baseAvg)
                + "% до " + percent.format(fullAvg) + "% (+" + percent.format(gap) + " п.п.)"
                : "среднее покрытие чуть ниже (" + percent.format(fullAvg) + "% против "
                + percent.format(baseAvg) + "%)";
        return "Пайплайн с query rewrite и фильтром: " + filterPart + recallPart
                + "; " + coveragePart + ".";
    }
}