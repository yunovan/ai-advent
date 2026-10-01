package com.yunovan.aiadvent.day21;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class Day21IndexFacade {

    private static final int PAGES_CHARS = 3000;
    private static final int MAX_RESULTS = 5;

    private record Probe(String query, String mustContain) {
    }

    private static final List<Probe> PROBES = List.of(
            new Probe("n грамм признаковый вектор текста", "n-грамм"),
            new Probe("разбиение документов на чанки по заголовкам", "заголовк"),
            new Probe("протокол MCP обмен сообщениями json rpc", "JSON-RPC"),
            new Probe("фоновый планировщик напоминаний и джобов", "напоминани"),
            new Probe("память агента краткосрочная и долговременная", "долговременн"),
            new Probe("рецепт борща из свёклы пошагово", "свёкл"));

    private final Day21CorpusLoader corpus;
    private final Day21EmbeddingService embedding;
    private final Day21IndexStore store;
    private final Day21Properties properties;
    private final Map<String, Day21Chunker> chunkers;
    private final Map<String, List<Day21IndexEntry>> cache = new ConcurrentHashMap<>();
    private final DecimalFormat percent;
    private final DecimalFormat cv;

    public Day21IndexFacade(Day21CorpusLoader corpus, Day21EmbeddingService embedding,
                            Day21IndexStore store, Day21Properties properties,
                            List<Day21Chunker> chunkers) {
        this.corpus = corpus;
        this.embedding = embedding;
        this.store = store;
        this.properties = properties;
        this.chunkers = new ConcurrentHashMap<>();
        for (Day21Chunker chunker : chunkers) {
            this.chunkers.put(chunker.strategy(), chunker);
        }
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        this.percent = new DecimalFormat("0.0", symbols);
        this.cv = new DecimalFormat("0.00", symbols);
    }

    public Day21HealthResponse health() {
        List<Day21Document> documents = corpus.loadDocuments();
        int corpusChars = documents.stream().mapToInt(d -> d.text().length()).sum();
        List<Day21StrategyInfo> strategies = new ArrayList<>();
        for (String strategy : List.of(Day21Properties.STRATEGY_FIXED, Day21Properties.STRATEGY_STRUCTURAL)) {
            Day21IndexFile file = store.load(strategy);
            int chunkCount = file == null || file.entries() == null ? 0 : file.entries().size();
            boolean indexed = cache.containsKey(strategy) || chunkCount > 0;
            strategies.add(new Day21StrategyInfo(strategy, description(strategy), indexed, chunkCount));
        }
        int pages = Math.max(1, (corpusChars + PAGES_CHARS - 1) / PAGES_CHARS);
        return new Day21HealthResponse(
                properties.name(), properties.version(), documents.size(), corpusChars, pages, strategies);
    }

    public Day21StrategyInfo strategyInfo(String strategy) {
        String name = properties.normalizeStrategy(strategy);
        Day21IndexFile file = store.load(name);
        int chunkCount = file == null || file.entries() == null ? 0 : file.entries().size();
        return new Day21StrategyInfo(name, description(name),
                cache.containsKey(name) || chunkCount > 0, chunkCount);
    }

    public Day21IngestResponse ingest(String strategy) {
        String name = properties.normalizeStrategy(strategy);
        Day21Chunker chunker = chunkers.get(name);
        if (chunker == null) {
            throw new IllegalArgumentException("Неизвестная стратегия чанкинга: " + name);
        }
        List<Day21Document> documents = corpus.loadDocuments();
        List<Day21IndexEntry> entries = new ArrayList<>();
        for (Day21Document document : documents) {
            for (Day21Chunk chunk : chunker.chunk(document)) {
                entries.add(new Day21IndexEntry(chunk, embedding.embed(chunk.text()).values()));
            }
        }
        int corpusChars = documents.stream().mapToInt(d -> d.text().length()).sum();
        Day21IndexFile file = new Day21IndexFile(name, documents.size(), corpusChars, entries);
        store.save(file);
        cache.put(name, entries);
        return new Day21IngestResponse(name, documents.size(), entries.size(),
                corpusChars, store.path(name).toString());
    }

    public List<Day21Chunk> chunks(String strategy) {
        String name = properties.normalizeStrategy(strategy);
        List<Day21IndexEntry> entries = ensure(name);
        return entries.stream().map(Day21IndexEntry::chunk).toList();
    }

    public Day21SearchResponse search(String strategy, String query, Integer k) {
        String name = properties.normalizeStrategy(strategy);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Запрос не может быть пустым");
        }
        int limit = k == null || k <= 0 ? 3 : Math.min(k, MAX_RESULTS);
        List<Day21IndexEntry> entries = ensure(name);
        Day21Embedding queryVector = embedding.embed(query.trim());
        List<Day21Scored> scored = new ArrayList<>();
        for (Day21IndexEntry entry : entries) {
            scored.add(new Day21Scored(entry,
                    Day21EmbeddingService.cosine(queryVector, new Day21Embedding(entry.vector()))));
        }
        scored.sort(Comparator.comparingDouble(Day21Scored::score).reversed());
        List<Day21SearchHit> hits = new ArrayList<>();
        for (int i = 0; i < Math.min(limit, scored.size()); i++) {
            Day21Scored item = scored.get(i);
            hits.add(new Day21SearchHit(item.entry().chunk(), item.score(), snippet(item.entry().chunk().text())));
        }
        return new Day21SearchResponse(name, query.trim(), limit, hits);
    }

    public Day21ComparisonResponse compare() {
        List<Day21StrategyMetric> metrics = new ArrayList<>();
        for (String strategy : List.of(Day21Properties.STRATEGY_FIXED, Day21Properties.STRATEGY_STRUCTURAL)) {
            metrics.add(strategyMetric(strategy));
        }
        Day21StrategyMetric fixed = metrics.get(0);
        Day21StrategyMetric structural = metrics.get(1);
        String verdict = verdict(fixed, structural);
        return new Day21ComparisonResponse(metrics,
                PROBES.stream().map(p -> p.query()).toList(), verdict);
    }

    private Day21StrategyMetric strategyMetric(String strategy) {
        String name = properties.normalizeStrategy(strategy);
        Day21IndexFile file = store.load(name);
        List<Day21IndexEntry> entries = file != null && file.entries() != null
                ? file.entries() : ensure(name);
        double[] sizes = entries.stream()
                .mapToDouble(e -> e.chunk().charCount()).toArray();
        double avg = average(sizes);
        double min = min(sizes);
        double max = max(sizes);
        double std = stddev(sizes, avg);
        double coefficient = avg == 0 ? 0 : std / avg;
        int probeHits = 0;
        for (Probe probe : PROBES) {
            if (hitsTop(entries, probe)) {
                probeHits++;
            }
        }
        double coverage = PROBES.isEmpty() ? 0
                : 100.0 * probeHits / PROBES.size();
        return new Day21StrategyMetric(
                name, file != null ? file.documents() : 0, entries.size(),
                file != null ? file.corpusChars() : 0,
                avg, min, max, std, coefficient, probeHits, PROBES.size(), coverage);
    }

    private boolean hitsTop(List<Day21IndexEntry> entries, Probe probe) {
        Day21Embedding queryVector = embedding.embed(probe.query());
        List<Day21Scored> scored = new ArrayList<>();
        for (Day21IndexEntry entry : entries) {
            scored.add(new Day21Scored(entry,
                    Day21EmbeddingService.cosine(queryVector, new Day21Embedding(entry.vector()))));
        }
        scored.sort(Comparator.comparingDouble(Day21Scored::score).reversed());
        String needle = probe.mustContain().toLowerCase(Locale.ROOT);
        for (int i = 0; i < Math.min(3, scored.size()); i++) {
            if (scored.get(i).entry().chunk().text().toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private List<Day21IndexEntry> ensure(String strategy) {
        if (cache.containsKey(strategy)) {
            return cache.get(strategy);
        }
        synchronized (this) {
            if (cache.containsKey(strategy)) {
                return cache.get(strategy);
            }
            Day21IndexFile file = store.load(strategy);
            if (file != null && file.entries() != null && !file.entries().isEmpty()) {
                cache.put(strategy, file.entries());
                return file.entries();
            }
            ingest(strategy);
            return cache.getOrDefault(strategy, List.of());
        }
    }

    private String verdict(Day21StrategyMetric fixed, Day21StrategyMetric structural) {
        Day21StrategyMetric better = structural.probeCoveragePercent() >= fixed.probeCoveragePercent()
                ? structural : fixed;
        if (better == structural && structural.probeCoveragePercent() == fixed.probeCoveragePercent()) {
            String message = "Обе стратегии дают одинаковое покрытие (%s%%), "
                    + "но «structural» заметно стабильнее по размеру чанков (CV %s против %s), "
                    + "поэтому для ответов по документам он предпочтительнее: "
                    + "чанки привязаны к заголовкам и параграфам, а не режут текст посередине мысли.";
            return String.format(message, percent.format(fixed.probeCoveragePercent()),
                    cv.format(structural.coefficientOfVariation()), cv.format(fixed.coefficientOfVariation()));
        }
        String message = "Стратегия «%s» оказывается точнее: покрытие %s%% против %s%% по %d пробе, "
                + "а размер чанков стабильнее (CV %s против %s). "
                + "Для задач поиска по документам это лучший вариант по умолчанию.";
        return String.format(message, better.strategy(), percent.format(better.probeCoveragePercent()),
                percent.format(other(fixed, structural, better).probeCoveragePercent()), PROBES.size(),
                cv.format(better.coefficientOfVariation()), cv.format(other(fixed, structural, better).coefficientOfVariation()));
    }

    private static Day21StrategyMetric other(Day21StrategyMetric a, Day21StrategyMetric b, Day21StrategyMetric one) {
        return one == a ? b : a;
    }

    private String description(String strategy) {
        return strategy.equals(Day21Properties.STRATEGY_STRUCTURAL)
                ? "По структуре: текст разбивается по заголовкам, параграфам и объявлениям классов"
                : "По фиксированному размеру: окно из ~" + properties.chunkSize() + " символов с перекрытием";
    }

    private static String snippet(String text) {
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 180) {
            return compact;
        }
        return compact.substring(0, 180).trim() + "…";
    }

    private record Day21Scored(Day21IndexEntry entry, double score) {
    }

    private static double average(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double min(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double result = values[0];
        for (double value : values) {
            result = Math.min(result, value);
        }
        return result;
    }

    private static double max(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double result = values[0];
        for (double value : values) {
            result = Math.max(result, value);
        }
        return result;
    }

    private static double stddev(double[] values, double average) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double value : values) {
            sum += (value - average) * (value - average);
        }
        return Math.sqrt(sum / values.length);
    }
}