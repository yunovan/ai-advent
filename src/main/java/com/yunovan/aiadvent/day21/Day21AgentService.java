package com.yunovan.aiadvent.day21;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmReply;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class Day21AgentService {

    private static final Logger log = LoggerFactory.getLogger(Day21AgentService.class);

    private static final List<String> INGEST_MARKERS = List.of(
            "проиндексиру", "индекс", "построй индекс", "переиндекс", "ingest",
            "собери индекс");
    private static final List<String> COMPARE_MARKERS = List.of(
            "сравни стратеги", "какая стратегия", "сравнение стратеги", "чанкинг",
            "какой чанкинг", "чunking");
    private static final List<String> SEARCH_MARKERS = List.of(
            "найди", "найти", "поиск", "поищи", "что такое", "где говорится",
            "что говорится", "упоминается", "найдите", "search");
    private static final List<String> STOP_WORDS = List.of("и", "в", "по", "на", "про", ",", "для", "мне",
            "нужно", "о", "об", "что", "такое", "где", "как", "из", "за", "или", "с", "к", "у");

    private final Day21IndexFacade facade;
    private final LlmClient llm;

    public Day21AgentService(Day21IndexFacade facade, LlmClient llm) {
        this.facade = facade;
        this.llm = llm;
    }

    public Day21AgentResponse submit(String prompt) {
        String promptText = prompt == null ? "" : prompt.trim();
        if (promptText.isBlank()) {
            throw new IllegalArgumentException("Запрос не может быть пустым");
        }
        Intent intent = parseIntent(promptText);
        String toolResult = execute(intent);
        String answer = phrase(promptText, intent.tool(), toolResult);
        Map<String, Object> args = new LinkedHashMap<>();
        if (intent.query != null) {
            args.put("query", intent.query);
            args.put("strategy", intent.strategy);
        } else {
            args.put("strategy", intent.strategy);
        }
        return new Day21AgentResponse(promptText, intent.kind, intent.tool, args, toolResult, answer);
    }

    private String execute(Intent intent) {
        return switch (intent.kind()) {
            case "ingest" -> {
                Day21IngestResponse response = facade.ingest(intent.strategy());
                yield "Индекс «" + response.strategy() + "» построен: " + response.documents()
                        + " документов, " + response.chunks() + " чанков, "
                        + response.corpusChars() + " символов. Файл: " + response.indexFile();
            }
            case "compare" -> {
                Day21ComparisonResponse response = facade.compare();
                yield "Сравнение стратегий чанкинга: " + response.verdict();
            }
            case "search" -> {
                Day21SearchResponse response = facade.search(intent.strategy(), intent.query(), 3);
                if (response.hits().isEmpty()) {
                    yield "По запросу «" + intent.query + "» ничего не найдено (стратегия "
                            + intent.strategy + ").";
                }
                StringBuilder text = new StringBuilder();
                text.append("Найдено по запросу «").append(response.query()).append("»:\n");
                for (Day21SearchHit hit : response.hits()) {
                    text.append("- ").append(hit.chunk().chunkId())
                            .append(" [").append(String.format("%.3f", hit.score())).append("] ")
                            .append(hit.chunk().source())
                            .append(" | ").append(hit.snippet()).append('\n');
                }
                yield text.toString().trim();
            }
            default -> throw new IllegalArgumentException("Неизвестное намерение");
        };
    }

    private String phrase(String prompt, String tool, String toolResult) {
        String message = "Пользователь: " + prompt + "\n"
                + "Инструмент локального индекса документов " + tool + " вернул результат:\n" + toolResult;
        String content = null;
        try {
            LlmReply reply = llm.complete(new CompletionCommand(message,
                    "Ты — ассистент проекта AI Advent. Кратко ответь пользователю по-русски, опираясь "
                            + "на результат поиска по локальному индексу документов. Цитируй источник "
                            + "(source и section), если они есть. Если инструмент вернул ошибку — честно "
                            + "скажи об этом.",
                    300, null));
            content = reply == null ? null : reply.content().trim();
        } catch (LlmException ex) {
            log.info("День 21: LLM недоступен, вернём сырой результат инструмента: {}", ex.getMessage());
        }
        if (content == null || content.isBlank()) {
            return "Результат инструмента " + tool + ":\n" + toolResult;
        }
        return content;
    }

    private Intent parseIntent(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        String strategy = detectStrategy(prompt);
        for (String marker : INGEST_MARKERS) {
            if (lower.contains(marker)) {
                return new Intent("ingest", "index_ingest", null, strategy);
            }
        }
        for (String marker : COMPARE_MARKERS) {
            if (lower.contains(marker)) {
                return new Intent("compare", "index_compare", null, strategy);
            }
        }
        for (String marker : SEARCH_MARKERS) {
            if (lower.contains(marker)) {
                String query = queryAfter(prompt, marker);
                if (query != null) {
                    return new Intent("search", "index_search", query, strategy);
                }
            }
        }
        String hint = "Я работаю с локальным индексом документов. Сформулируйте запрос иначе, например:\n"
                + "  найди что такое эмбеддинги\n"
                + "  проиндексируй корпус\n"
                + "  сравни стратегии чанкинга";
        throw new IllegalArgumentException(hint);
    }

    private static String detectStrategy(String prompt) {
        return prompt.toLowerCase(Locale.ROOT).contains("структур")
                ? Day21Properties.STRATEGY_STRUCTURAL : Day21Properties.STRATEGY_FIXED;
    }

    private static String queryAfter(String prompt, String marker) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(marker);
        if (index < 0) {
            return null;
        }
        String after = index + marker.length() < prompt.length()
                ? prompt.substring(index + marker.length()) : "";
        StringBuilder query = new StringBuilder();
        String format = "структур"; 
        for (String token : after.split("\\s+")) {
            String clean = token.replaceAll("[,.!?;:()\"«»—]", "");
            String lowerToken = clean.toLowerCase(Locale.ROOT);
            if (clean.isBlank() || lowerToken.contains(format)
                    || STOP_WORDS.contains(lowerToken)) {
                continue;
            }
            query.append(clean).append(' ');
            if (query.toString().split(" ").length >= 8) {
                break;
            }
        }
        String result = query.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private record Intent(String kind, String tool, String query, String strategy) {
    }
}