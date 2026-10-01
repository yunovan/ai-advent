package com.yunovan.aiadvent.day22;

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
public class Day22RagService {

    private static final Logger log = LoggerFactory.getLogger(Day22RagService.class);

    public static final String MODE_RAG = "rag";
    public static final String MODE_PLAIN = "plain";

    private static final String RAG_SYSTEM =
            "Ты — ассистент проекта AI Advent. Отвечай пользователю по-русски, опираясь ТОЛЬКО "
                    + "на контекст документов. Если ответа нет в контексте — честно скажи, что в индексе "
                    + "этого нет. Для каждого факта указывай источник и раздел, откуда он взят.";

    private static final String PLAIN_SYSTEM =
            "Ты — ассистент проекта AI Advent. Отвечай пользователю по-русски, используя только свои "
                    + "знания, без внешнего контекста документов.";

    private static final List<String> PLAIN_MARKERS = List.of(
            "без rag", "без контекста", "по своей памяти", "своими словами",
            "без поиска", "без индекса", "без документов", "без внешнего контекста",
            "не используй контекст", "plain");

    private final Day21IndexFacade facade;
    private final Day22Properties properties;
    private final LlmClient llm;
    private final DecimalFormat percent;

    public Day22RagService(Day21IndexFacade facade, Day22Properties properties, LlmClient llm) {
        this.facade = facade;
        this.properties = properties;
        this.llm = llm;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        this.percent = new DecimalFormat("0.0", symbols);
    }

    public Day22HealthResponse health() {
        Day21HealthResponse base = facade.health();
        return new Day22HealthResponse(base.documents(), base.corpusChars(), base.pagesEstimate(),
                properties.normalizedStrategy(), properties.topK(), properties.answerMaxTokens(),
                base.strategies());
    }

    public List<Day22ControlQuestion> questions() {
        return Day22ControlQuestions.ALL;
    }

    public Day22AnswerResponse answer(String question, String mode) {
        String questionText = question == null ? "" : question.trim();
        if (questionText.isBlank()) {
            throw new IllegalArgumentException("Вопрос не может быть пустым");
        }
        String usedMode = normalizeMode(mode);
        List<Day21SearchHit> hits = usedMode.equals(MODE_RAG) ? retrieve(questionText) : List.of();
        String content = usedMode.equals(MODE_RAG)
                ? complete(ragPrompt(questionText, hits), RAG_SYSTEM)
                : complete(questionText, PLAIN_SYSTEM);
        boolean fallback = content == null;
        if (fallback) {
            content = usedMode.equals(MODE_RAG) ? ragFallback(questionText, hits) : plainFallback(questionText);
        }
        return new Day22AnswerResponse(questionText, usedMode, hits, content, fallback);
    }

    private List<Day21SearchHit> retrieve(String question) {
        String strategy = properties.normalizedStrategy();
        Map<String, Double> dense = new HashMap<>();
        for (Day21SearchHit hit : facade.search(strategy, question, properties.topK()).hits()) {
            dense.put(hit.chunk().chunkId(), hit.score());
        }
        return Day22ReRanker.rank(question, facade.chunks(strategy), dense, properties.topK());
    }

    public Day22AnswerResponse ask(String prompt) {
        String promptText = prompt == null ? "" : prompt.trim();
        if (promptText.isBlank()) {
            throw new IllegalArgumentException("Вопрос не может быть пустым");
        }
        return answer(promptText, autoMode(promptText));
    }

    public Day22CompareResponse compare(String question) {
        String questionText = question == null ? "" : question.trim();
        if (questionText.isBlank()) {
            throw new IllegalArgumentException("Вопрос не может быть пустым");
        }
        Day22AnswerResponse rag = answer(questionText, MODE_RAG);
        Day22AnswerResponse plain = answer(questionText, MODE_PLAIN);
        List<String> sources = rag.retrievedHits().stream()
                .map(hit -> hit.chunk().fileName())
                .distinct()
                .toList();
        String verdict = sources.isEmpty()
                ? "По запросу в индексе ничего не нашлось: модель с RAG и без RAG работает на одной базе."
                : "С RAG модель получила контекст из " + sources.size()
                        + " источников (" + String.join(", ", sources)
                        + "), а без RAG отвечает только по своей памяти.";
        return new Day22CompareResponse(questionText, rag, plain, sources, verdict);
    }

    public Day22EvalResponse evaluate() {
        List<Day22EvalItem> items = new ArrayList<>();
        for (Day22ControlQuestion question : Day22ControlQuestions.ALL) {
            Day22AnswerResponse rag = answer(question.question(), MODE_RAG);
            Day22AnswerResponse plain = answer(question.question(), MODE_PLAIN);
            List<String> retrieved = rag.retrievedHits().stream()
                    .map(hit -> hit.chunk().fileName())
                    .distinct()
                    .toList();
            boolean retrievalHit = question.expectedSources().stream().anyMatch(retrieved::contains);
            double ragCoverage = 100.0 * keywordCoverage(question.expectedKeywords(), rag.answer());
            double plainCoverage = 100.0 * keywordCoverage(question.expectedKeywords(), plain.answer());
            items.add(new Day22EvalItem(question.id(), question.question(),
                    question.expectedKeywords(), question.expectedSources(), retrieved, retrievalHit,
                    ragCoverage, plainCoverage, ragCoverage - plainCoverage));
        }
        int retrievalHits = (int) items.stream().filter(Day22EvalItem::retrievalHit).count();
        double recall = 100.0 * retrievalHits / items.size();
        double ragAvg = items.stream().mapToDouble(Day22EvalItem::ragCoveragePercent).average().orElse(0);
        double plainAvg = items.stream().mapToDouble(Day22EvalItem::plainCoveragePercent).average().orElse(0);
        double gap = ragAvg - plainAvg;
        return new Day22EvalResponse(items.size(), retrievalHits, recall,
                ragAvg, plainAvg, gap, verdict(recall, ragAvg, plainAvg, gap), items);
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
            log.info("День 22: LLM недоступен, сформируем ответ из контекста индекса: {}", ex.getMessage());
        }
        return null;
    }

    private static String ragPrompt(String question, List<Day21SearchHit> hits) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Вопрос пользователя: «").append(question).append("»\n\n");
        if (hits.isEmpty()) {
            prompt.append("По запросу в индексе документов ничего не найдено.\n");
            return prompt.toString();
        }
        prompt.append("Контекст документов (результат поиска по локальному индексу):\n");
        for (Day21SearchHit hit : hits) {
            prompt.append("\n[Источник: ").append(hit.chunk().source())
                    .append(", раздел: «").append(section(hit)).append("»")
                    .append(", чанк: ").append(hit.chunk().chunkId()).append("]\n")
                    .append(hit.chunk().text()).append('\n');
        }
        return prompt.toString();
    }

    private static String ragFallback(String question, List<Day21SearchHit> hits) {
        StringBuilder text = new StringBuilder();
        text.append("Запрос: «").append(question).append("»\n");
        text.append("Ответ сформирован из контекста индекса (LLM недоступен):\n");
        if (hits.isEmpty()) {
            text.append("По запросу в индексе — ничего не найдено.\n");
            return text.toString().trim();
        }
        for (Day21SearchHit hit : hits) {
            text.append("- [").append(hit.chunk().source())
                    .append(", раздел «").append(section(hit)).append("», score ")
                    .append(String.format(Locale.ROOT, "%.3f", hit.score())).append("]:\n")
                    .append(hit.chunk().text()).append("\n\n");
        }
        return text.toString().trim();
    }

    private static String plainFallback(String question) {
        return "Запрос: «" + question + "». Без RAG модель ответила бы только из своих знаний; "
                + "в офлайн-демо LLM недоступен, поэтому ответ без контекста документов не сформирован.";
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
            return MODE_RAG;
        }
        String lower = mode.trim().toLowerCase(Locale.ROOT);
        if (lower.equals(MODE_PLAIN) || lower.equals("no") || lower.equals("без")
                || lower.equals("false") || lower.contains("без")) {
            return MODE_PLAIN;
        }
        return MODE_RAG;
    }

    private static String autoMode(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        for (String marker : PLAIN_MARKERS) {
            if (lower.contains(marker)) {
                return MODE_PLAIN;
            }
        }
        return MODE_RAG;
    }

    private String verdict(double recall, double ragAvg, double plainAvg, double gap) {
        String recallPart = "RAG нашёл ожидаемый источник в "
                + percent.format(recall) + "% контрольных вопросов";
        if (gap >= 0) {
            return recallPart + "; среднее покрытие ключевых слов: с RAG "
                    + percent.format(ragAvg) + "% против " + percent.format(plainAvg)
                    + "% без RAG (разница +" + percent.format(gap)
                    + " п.п.). Вердикт: режим с RAG даёт более точные, подтверждаемые источниками ответы.";
        }
        return recallPart + "; модель справляется и без контекста (покрытие "
                + percent.format(plainAvg) + "% против " + percent.format(ragAvg)
                + "% с RAG), но RAG добавляет ссылки на источники и снижает риск галлюцинаций.";
    }
}