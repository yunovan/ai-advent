package com.yunovan.aiadvent.day28;

import com.yunovan.aiadvent.day21.Day21HealthResponse;
import com.yunovan.aiadvent.day21.Day21IndexFacade;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day22.Day22ControlQuestion;
import com.yunovan.aiadvent.day22.Day22ControlQuestions;
import com.yunovan.aiadvent.day23.Day23QueryRewriter;
import com.yunovan.aiadvent.day23.Day23ReRanker;
import com.yunovan.aiadvent.day24.Day24Properties;
import com.yunovan.aiadvent.day24.Day24Quote;
import com.yunovan.aiadvent.day24.Day24QuoteEngine;
import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmProperties;
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
public class Day28RagService {

    private static final Logger log = LoggerFactory.getLogger(Day28RagService.class);

    public static final String ENGINE_LOCAL = "local";
    public static final String ENGINE_CLOUD = "cloud";

    public static final String RETRIEVAL_NOTE =
            "локальный: n-gram эмбеддинги + cosine + BM25-переранжирование, без сетевых вызовов";

    public static final String SYSTEM =
            "Ты — ассистент проекта AI Advent. Отвечай пользователю по-русски, опираясь ТОЛЬКО "
                    + "на переданный контекст документов. Для каждого факта указывай источник (файл) "
                    + "и раздел. Если в контексте нет ответа — честно скажи об этом, не выдумывай факты.";

    private static final String CLOUD_NO_KEY = "облачный ключ не задан (LLM_API_KEY)";

    private static final Day24Properties QUOTE_PROPERTIES = new Day24Properties(
            null, null, null, null, null, null, null, null, null, null);

    private final Day21IndexFacade facade;
    private final Day28Properties properties;
    private final Day26Properties localProperties;
    private final Day26LocalLlmClient localLlm;
    private final LlmClient cloudLlm;
    private final LlmProperties cloudProperties;
    private final DecimalFormat one;
    private final DecimalFormat latency;

    public Day28RagService(Day21IndexFacade facade, Day28Properties properties,
                           Day26Properties localProperties, Day26LocalLlmClient localLlm,
                           LlmClient cloudLlm, LlmProperties cloudProperties) {
        this.facade = facade;
        this.properties = properties;
        this.localProperties = localProperties;
        this.localLlm = localLlm;
        this.cloudLlm = cloudLlm;
        this.cloudProperties = cloudProperties;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        this.one = new DecimalFormat("0.0", symbols);
        this.latency = new DecimalFormat("0", symbols);
    }

    public Day28HealthResponse health() {
        Day21HealthResponse base = facade.health();
        LocalProbe probe = probeLocal();
        return new Day28HealthResponse(base.documents(), base.corpusChars(), base.pagesEstimate(),
                properties.normalizedStrategy(), properties.topKBefore(), properties.topKAfter(),
                properties.threshold(), properties.rewrite(), properties.answerMaxTokens(),
                properties.evaluateRuns(), RETRIEVAL_NOTE,
                localProperties.endpoint(), localProperties.model(),
                probe.available(), probe.version(), probe.installed(),
                cloudProperties.hasApiKey(), cloudModel(), false, probe.reason());
    }

    public List<Day22ControlQuestion> questions() {
        return Day22ControlQuestions.ALL;
    }

    public Day28AnswerResponse ask(String question) {
        String questionText = requireQuestion(question);
        return localAnswer(questionText, retrieve(questionText));
    }

    public Day28CompareResponse compare(String question) {
        String questionText = requireQuestion(question);
        Pipeline pipeline = retrieve(questionText);
        Day28AnswerResponse local = localAnswer(questionText, pipeline);
        Day28AnswerResponse cloud = cloudAnswer(questionText, pipeline);
        List<String> sources = sources(pipeline.hits());
        return new Day28CompareResponse(questionText, local, cloud,
                cloudProperties.hasApiKey(), sources, compareVerdict(local, cloud, sources));
    }

    public Day28EvalResponse evaluate() {
        int runs = properties.evaluateRuns();
        LocalProbe probe = probeLocal();
        boolean localUsable = probe.available() && probe.installed();
        String localReason = !probe.available() ? probe.reason()
                : !probe.installed() ? "модель " + localProperties.model() + " не установлена в Ollama"
                : "";
        boolean cloudEnabled = cloudProperties.hasApiKey();
        String cloudFailure = "";

        List<Day28EvalItem> items = new ArrayList<>();
        List<Double> localCoverages = new ArrayList<>();
        List<Long> localLatencies = new ArrayList<>();
        List<Double> localTps = new ArrayList<>();
        int localAnswered = 0;
        int localFallbacks = 0;
        List<Double> cloudCoverages = new ArrayList<>();
        List<Long> cloudLatencies = new ArrayList<>();
        List<Double> cloudTps = new ArrayList<>();
        int cloudAnswered = 0;

        for (Day22ControlQuestion question : Day22ControlQuestions.ALL) {
            Pipeline pipeline = retrieve(question.question());
            List<String> retrieved = sources(pipeline.hits());
            boolean hit = question.expectedSources().stream().anyMatch(retrieved::contains);

            List<Double> questionLocalCoverages = new ArrayList<>();
            List<Long> questionLocalLatencies = new ArrayList<>();
            if (localUsable) {
                for (int run = 0; run < runs; run++) {
                    Day28AnswerResponse answer = localAnswer(question.question(), pipeline);
                    localAnswered++;
                    if (answer.fallback()) {
                        localFallbacks++;
                    }
                    double coverage = 100.0
                            * keywordCoverage(question.expectedKeywords(), answer.answer());
                    questionLocalCoverages.add(coverage);
                    questionLocalLatencies.add(answer.latencyMs());
                    localCoverages.add(coverage);
                    localLatencies.add(answer.latencyMs());
                    if (answer.tokensPerSecond() > 0) {
                        localTps.add(answer.tokensPerSecond());
                    }
                }
            }

            List<Double> questionCloudCoverages = new ArrayList<>();
            List<Long> questionCloudLatencies = new ArrayList<>();
            if (cloudEnabled && cloudFailure.isEmpty()) {
                for (int run = 0; run < runs; run++) {
                    Day28AnswerResponse answer = cloudAnswer(question.question(), pipeline);
                    if (!answer.unavailableReason().isEmpty()) {
                        cloudFailure = answer.unavailableReason();
                        break;
                    }
                    cloudAnswered++;
                    double coverage = 100.0
                            * keywordCoverage(question.expectedKeywords(), answer.answer());
                    questionCloudCoverages.add(coverage);
                    questionCloudLatencies.add(answer.latencyMs());
                    cloudCoverages.add(coverage);
                    cloudLatencies.add(answer.latencyMs());
                    if (answer.tokensPerSecond() > 0) {
                        cloudTps.add(answer.tokensPerSecond());
                    }
                }
            }

            items.add(new Day28EvalItem(question.id(), question.question(),
                    question.expectedKeywords(), question.expectedSources(), retrieved, hit,
                    questionLocalCoverages, questionLocalLatencies,
                    questionCloudCoverages, questionCloudLatencies));
        }

        int retrievalHits = (int) items.stream().filter(Day28EvalItem::retrievalHit).count();
        double recall = Math.round(1000.0 * retrievalHits / Math.max(1, items.size())) / 10.0;

        Day28EngineStats local = localUsable
                ? new Day28EngineStats(true, localFallbacks > 0
                        ? "в " + localFallbacks + " из " + localAnswered
                                + " прогонов локальная LLM не ответила — они посчитаны фолбэком"
                        : "",
                localAnswered, localFallbacks,
                average(localCoverages),
                avgStdDev(items.stream().map(Day28EvalItem::localCoverages).toList()),
                averageMs(localLatencies),
                avgStdDevLong(items.stream().map(Day28EvalItem::localLatencies).toList()),
                average(localTps))
                : Day28EngineStats.unavailable(localReason);

        Day28EngineStats cloud;
        if (!cloudEnabled) {
            cloud = Day28EngineStats.unavailable(CLOUD_NO_KEY);
        } else if (!cloudFailure.isEmpty()) {
            cloud = Day28EngineStats.unavailable(cloudAnswered > 0
                    ? "облачная LLM недоступна: " + cloudFailure + " (прервано после "
                            + cloudAnswered + " успешных прогонов)"
                    : "облачная LLM недоступна: " + cloudFailure);
        } else {
            cloud = new Day28EngineStats(true, "",                     cloudAnswered, 0,
                    average(cloudCoverages),
                    avgStdDev(items.stream().map(Day28EvalItem::cloudCoverages).toList()),
                    averageMs(cloudLatencies),
                    avgStdDevLong(items.stream().map(Day28EvalItem::cloudLatencies).toList()),
                    average(cloudTps));
        }

        return new Day28EvalResponse(items.size(), runs, retrievalHits, recall, local, cloud,
                qualityVerdict(local, cloud, items.size()),
                speedVerdict(local, cloud),
                stabilityVerdict(runs, local, cloud),
                items);
    }

    private Day28AnswerResponse localAnswer(String question, Pipeline pipeline) {
        String prompt = prompt(question, pipeline);
        long started = System.nanoTime();
        try {
            var answer = localLlm.chat(SYSTEM, prompt);
            String content = answer.reply();
            return new Day28AnswerResponse(question, ENGINE_LOCAL, answer.model(),
                    pipeline.matchedQuery(), pipeline.rewritten(), pipeline.candidatesBefore(),
                    pipeline.filteredOut(), pipeline.hits(), sources(pipeline.hits()),
                    content, false, grounding(content, pipeline),
                    answer.latencyMs(), answer.promptTokens(), answer.outputTokens(),
                    answer.tokensPerSecond(), "");
        } catch (Day26LlmException ex) {
            long elapsedMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            log.info("День 28: локальная LLM недоступна, ответ собирается из индекса: {}",
                    ex.getMessage());
            String content = fallbackAnswer(question, pipeline);
            return new Day28AnswerResponse(question, ENGINE_LOCAL, localProperties.model(),
                    pipeline.matchedQuery(), pipeline.rewritten(), pipeline.candidatesBefore(),
                    pipeline.filteredOut(), pipeline.hits(), sources(pipeline.hits()),
                    content, true, grounding(content, pipeline),
                    elapsedMs, 0, 0, 0.0, ex.getMessage());
        }
    }

    private Day28AnswerResponse cloudAnswer(String question, Pipeline pipeline) {
        if (!cloudProperties.hasApiKey()) {
            return cloudUnavailable(question, pipeline, CLOUD_NO_KEY);
        }
        String prompt = prompt(question, pipeline);
        try {
            LlmReply reply = cloudLlm.complete(new CompletionCommand(prompt, SYSTEM,
                    properties.answerMaxTokens(), null));
            String content = reply == null || reply.content() == null
                    ? "" : reply.content().trim();
            if (content.isEmpty()) {
                return cloudUnavailable(question, pipeline, "облачная LLM вернула пустой ответ");
            }
            int promptTokens = reply.promptTokens() == null ? 0 : reply.promptTokens();
            int outputTokens = reply.completionTokens() == null ? 0 : reply.completionTokens();
            double tokensPerSecond = reply.elapsedMs() > 0 && outputTokens > 0
                    ? Math.round(outputTokens * 1000.0 / reply.elapsedMs() * 10.0) / 10.0
                    : 0.0;
            return new Day28AnswerResponse(question, ENGINE_CLOUD, cloudModel(),
                    pipeline.matchedQuery(), pipeline.rewritten(), pipeline.candidatesBefore(),
                    pipeline.filteredOut(), pipeline.hits(), sources(pipeline.hits()),
                    content, false, grounding(content, pipeline),
                    reply.elapsedMs(), promptTokens, outputTokens, tokensPerSecond, "");
        } catch (LlmException ex) {
            log.info("День 28: облачная LLM недоступна: {}", ex.getMessage());
            return cloudUnavailable(question, pipeline, ex.getMessage());
        }
    }

    private Day28AnswerResponse cloudUnavailable(String question, Pipeline pipeline, String reason) {
        return new Day28AnswerResponse(question, ENGINE_CLOUD, cloudModel(),
                pipeline.matchedQuery(), pipeline.rewritten(), pipeline.candidatesBefore(),
                pipeline.filteredOut(), pipeline.hits(), sources(pipeline.hits()),
                "", false, null, 0, 0, 0, 0.0, reason);
    }

    public Pipeline retrieve(String question) {
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

    public String prompt(String question, Pipeline pipeline) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Вопрос пользователя: «").append(question).append("»\n\n");
        if (pipeline.rewritten()) {
            prompt.append("Перед поиском запрос был переписан: «")
                    .append(pipeline.matchedQuery()).append("»\n\n");
        }
        if (pipeline.filteredOut() > 0) {
            prompt.append("Из ").append(pipeline.candidatesBefore())
                    .append(" кандидатов ").append(pipeline.filteredOut())
                    .append(" отсеяны как нерелевантные (score ниже порога ")
                    .append(String.format(Locale.ROOT, "%.2f", properties.threshold()))
                    .append(").\n");
        }
        if (pipeline.hits().isEmpty()) {
            prompt.append("По запросу в индексе документов ничего не найдено.\n");
            return prompt.toString();
        }
        prompt.append("Контекст документов (результат поиска по локальному индексу):\n");
        for (Day21SearchHit hit : pipeline.hits()) {
            prompt.append("\n[Источник: ").append(hit.chunk().source())
                    .append(", раздел: «").append(section(hit)).append("»")
                    .append(", чанк: ").append(hit.chunk().chunkId()).append("]\n")
                    .append(hit.chunk().text()).append('\n');
        }
        prompt.append("\nПравила ответа:\n")
                .append("- отвечай по существу, опираясь только на контекст выше;\n")
                .append("- для каждого факта укажи источник (файл) и раздел;\n")
                .append("- если в контексте нет ответа — так и скажи.\n");
        return prompt.toString();
    }

    public static String fallbackAnswer(String question, Pipeline pipeline) {
        StringBuilder text = new StringBuilder();
        text.append("Запрос: «").append(question).append("»\n");
        text.append("Ответ собран из контекста локального индекса (локальная LLM недоступна):\n");
        if (pipeline.hits().isEmpty()) {
            text.append("По запросу — ничего не найдено.\n");
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

    public static Double grounding(String answer, Pipeline pipeline) {
        if (pipeline.hits().isEmpty()) {
            return null;
        }
        List<Day24Quote> quotes = Day24QuoteEngine.extract(pipeline.hits(),
                pipeline.matchedQuery(), QUOTE_PROPERTIES);
        if (quotes.isEmpty()) {
            return 0.0;
        }
        return 100.0 * Day24QuoteEngine.supportCoverage(answer, quotes);
    }

    private LocalProbe probeLocal() {
        try {
            String version = localLlm.version();
            boolean installed = localLlm.models().stream()
                    .anyMatch(m -> matches(m.name(), localProperties.model()));
            return new LocalProbe(true, version, installed, "");
        } catch (Day26LlmException ex) {
            return new LocalProbe(false, "", false, ex.getMessage());
        } catch (RuntimeException ex) {
            return new LocalProbe(false, "", false,
                    "не удалось проверить локальную LLM: " + message(ex));
        }
    }

    private String compareVerdict(Day28AnswerResponse local, Day28AnswerResponse cloud,
                                  List<String> sources) {
        StringBuilder text = new StringBuilder();
        if (sources.isEmpty()) {
            text.append("По запросу в локальном индексе ничего не нашлось — обе модели получили "
                    + "один и тот же пустой контекст. ");
        } else {
            text.append("Один и тот же локальный контекст из ").append(sources.size())
                    .append(" источников (").append(String.join(", ", sources))
                    .append(") отправлен обеим моделям. ");
        }
        if (!cloud.unavailableReason().isEmpty()) {
            text.append("Сравнить не с чем: облачная модель недоступна — ")
                    .append(cloud.unavailableReason()).append('.');
            return text.toString();
        }
        if (local.fallback()) {
            text.append("Локальная LLM не ответила, её ответ собран из контекста индекса. ");
        }
        text.append("Опора на контекст: локальная ").append(quality(local))
                .append(" против облачной ").append(quality(cloud)).append(". ");
        text.append("Скорость: локальная ").append(local.latencyMs()).append(" мс (")
                .append(one.format(local.tokensPerSecond())).append(" ток/с) против облачной ")
                .append(cloud.latencyMs()).append(" мс (")
                .append(one.format(cloud.tokensPerSecond())).append(" ток/с).");
        String faster = local.latencyMs() <= cloud.latencyMs() ? "локальная" : "облачная";
        long gap = Math.abs(local.latencyMs() - cloud.latencyMs());
        text.append(" Быстрее — ").append(faster).append(" модель (разница ")
                .append(gap).append(" мс).");
        return text.toString();
    }

    private String qualityVerdict(Day28EngineStats local, Day28EngineStats cloud, int total) {
        if (!local.available() && !cloud.available()) {
            return "Качество не измерено: ни локальная, ни облачная модель недоступны. "
                    + "Локальная: " + local.reason() + " Облачная: " + cloud.reason();
        }
        StringBuilder text = new StringBuilder();
        if (!local.available()) {
            text.append("Локальная модель не участвовала: ").append(local.reason()).append(' ');
        } else if (local.avgCoveragePercent() == null) {
            text.append("Локальная модель не ответила ни на один вопрос — качество не измерено. ");
        } else {
            text.append("Качество (покрытие ключевых слов на ").append(total)
                    .append(" контрольных вопросах): локальная модель ")
                    .append(one.format(local.avgCoveragePercent())).append('%');
            if (cloud.available() && cloud.avgCoveragePercent() != null) {
                double gap = local.avgCoveragePercent() - cloud.avgCoveragePercent();
                text.append(" против облачной ").append(one.format(cloud.avgCoveragePercent()))
                        .append("% (разница ").append(gap >= 0 ? "+" : "")
                        .append(one.format(gap)).append(" п.п.)");
            } else {
                text.append("; облачная модель не участвовала: ").append(cloud.reason());
            }
            text.append('.');
            if (local.fallbacks() > 0) {
                text.append(" Локальных фолбэков: ").append(local.fallbacks())
                        .append(" — они учтены в метрике, но качество модели не отражают.");
            }
        }
        return text.toString().trim();
    }

    private String speedVerdict(Day28EngineStats local, Day28EngineStats cloud) {
        if (local.avgLatencyMs() == null && cloud.avgLatencyMs() == null) {
            return "Скорость не измерена: ни одна модель не ответила.";
        }
        StringBuilder text = new StringBuilder("Средняя задержка на ответ: локальная ");
        text.append(local.avgLatencyMs() == null ? "н/д"
                : latency.format(local.avgLatencyMs()) + " мс");
        if (cloud.avgLatencyMs() != null) {
            text.append(" против облачной ").append(latency.format(cloud.avgLatencyMs())).append(" мс");
        } else {
            text.append("; облачная не участвовала: ").append(cloud.reason());
        }
        text.append('.');
        if (local.avgLatencyMs() != null && cloud.avgLatencyMs() != null) {
            String faster = local.avgLatencyMs() <= cloud.avgLatencyMs() ? "локальная" : "облачная";
            long gap = Math.round(Math.abs(local.avgLatencyMs() - cloud.avgLatencyMs()));
            text.append(" Быстрее — ").append(faster).append(" (разница ")
                    .append(gap).append(" мс).");
        }
        if (local.avgTokensPerSecond() != null) {
            text.append(" Пропускная способность: локальная ")
                    .append(one.format(local.avgTokensPerSecond())).append(" ток/с");
            if (cloud.avgTokensPerSecond() != null) {
                text.append(" против облачной ")
                        .append(one.format(cloud.avgTokensPerSecond())).append(" ток/с");
            }
            text.append('.');
        }
        return text.toString();
    }

    private String stabilityVerdict(int runs, Day28EngineStats local, Day28EngineStats cloud) {
        if (runs < 2) {
            return "Повторов на вопрос: " + runs
                    + " — стабильность не измерялась (увеличьте day28.evaluate-runs).";
        }
        if (local.coverageStdDev() == null && cloud.coverageStdDev() == null) {
            return "Стабильность не измерена: нет повторных ответов ни одной модели.";
        }
        StringBuilder text = new StringBuilder("Отклонение покрытия между повторами: ");
        if (local.coverageStdDev() != null) {
            text.append("локальная ±").append(one.format(local.coverageStdDev()))
                    .append(" п.п. — ").append(stabilityNote(local.coverageStdDev()));
        } else {
            text.append("локальная недоступна (").append(local.reason()).append(')');
        }
        if (cloud.coverageStdDev() != null) {
            text.append("; облачная ±").append(one.format(cloud.coverageStdDev()))
                    .append(" п.п. — ").append(stabilityNote(cloud.coverageStdDev()));
        } else {
            text.append("; облачная недоступна (").append(cloud.reason()).append(')');
        }
        text.append('.');
        return text.toString();
    }

    private static String stabilityNote(double stdDev) {
        if (stdDev < 5) {
            return "ответы стабильны";
        }
        if (stdDev < 15) {
            return "ответы заметно колеблются";
        }
        return "ответы сильно меняются между повторами";
    }

    private String quality(Day28AnswerResponse answer) {
        return answer.groundingPercent() == null
                ? "н/д" : one.format(answer.groundingPercent()) + '%';
    }

    private String cloudModel() {
        return cloudProperties.model() == null || cloudProperties.model().isBlank()
                ? "" : cloudProperties.model().trim();
    }

    public static String requireQuestion(String question) {
        String questionText = question == null ? "" : question.trim();
        if (questionText.isBlank()) {
            throw new IllegalArgumentException("Вопрос не может быть пустым");
        }
        return questionText;
    }

    public static String section(Day21SearchHit hit) {
        return hit.chunk().section().isEmpty() ? hit.chunk().title() : hit.chunk().section();
    }

    public static List<String> sources(List<Day21SearchHit> hits) {
        return hits.stream().map(hit -> hit.chunk().fileName()).distinct().toList();
    }

    public static double keywordCoverage(List<String> keywords, String answer) {
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

    private static Double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static Double averageMs(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    private static Double avgStdDev(List<List<Double>> perQuestion) {
        double sum = 0;
        int counted = 0;
        for (List<Double> values : perQuestion) {
            if (values == null || values.size() < 2) {
                continue;
            }
            double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double variance = values.stream()
                    .mapToDouble(value -> (value - avg) * (value - avg)).sum() / values.size();
            sum += Math.sqrt(variance);
            counted++;
        }
        return counted == 0 ? null : sum / counted;
    }

    private static Double avgStdDevLong(List<List<Long>> perQuestion) {
        double sum = 0;
        int counted = 0;
        for (List<Long> values : perQuestion) {
            if (values == null || values.size() < 2) {
                continue;
            }
            double avg = values.stream().mapToLong(Long::longValue).average().orElse(0);
            double variance = values.stream()
                    .mapToDouble(value -> (value - avg) * (value - avg)).sum() / values.size();
            sum += Math.sqrt(variance);
            counted++;
        }
        return counted == 0 ? null : sum / counted;
    }

    private static boolean matches(String installed, String expected) {
        if (installed == null || expected == null) {
            return false;
        }
        return installed.trim().toLowerCase(Locale.ROOT)
                .equals(expected.trim().toLowerCase(Locale.ROOT));
    }

    private static String message(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private record LocalProbe(boolean available, String version, boolean installed, String reason) {
    }

    public record Pipeline(String question, String matchedQuery, boolean rewritten,
                            int candidatesBefore, int filteredOut, List<Day21SearchHit> hits) {
    }
}
