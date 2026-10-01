package com.yunovan.aiadvent.day23;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Day23ReRanker {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final double K1 = 1.2;
    private static final double B = 0.75;
    private static final double DENSE_WEIGHT = 0.45;
    private static final double LEXICAL_WEIGHT = 0.55;

    private static final Set<String> STOPWORDS = Collections.unmodifiableSet(
            new HashSet<>(List.of(
            "и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а", "то",
            "все", "она", "так", "его", "но", "да", "ты", "к", "у", "же", "вы", "за",
            "по", "при", "об", "о", "из", "от", "для", "или", "это", "их", "быть",
            "чем", "если", "мы", "кто", "какой", "какая", "какие", "зачем", "уже",
            "можно", "должен", "эта", "эти", "того", "всех", "будет", "только",
            "чтобы", "который", "которая", "которые", "этот", "какое", "также",
            "свой", "своей", "своих", "между", "потому", "затем", "причем", "пока",
            "где", "тут", "там", "здесь", "ещё", "еще", "всё", "могут", "можно")));

    private Day23ReRanker() {
    }

    public static List<Day21SearchHit> rank(String query, List<Day21Chunk> allChunks,
                                            Map<String, Double> denseScores) {
        if (allChunks == null || allChunks.isEmpty()) {
            return List.of();
        }
        List<String> terms = terms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        double[] df = documentFrequencies(terms, allChunks);
        int docs = allChunks.size();
        Map<String, Double> idf = new HashMap<>();
        for (int i = 0; i < terms.size(); i++) {
            double value = Math.log((docs - df[i] + 0.5) / (df[i] + 0.5) + 1.0);
            idf.put(terms.get(i), value);
        }
        double avgdl = allChunks.stream().mapToDouble(Day23ReRanker::tokenCount).average().orElse(1);
        double bm25Largest = 0;
        List<ScoredChunk> scored = new ArrayList<>();
        for (Day21Chunk chunk : allChunks) {
            double bm25 = bm25(chunk, terms, idf, avgdl);
            bm25Largest = Math.max(bm25Largest, bm25);
            scored.add(new ScoredChunk(chunk, bm25));
        }
        double denseLargest = denseScores.values().stream()
                .mapToDouble(Double::doubleValue).max().orElse(1.0);
        if (denseLargest <= 0) {
            denseLargest = 1.0;
        }
        if (bm25Largest <= 0) {
            bm25Largest = 1.0;
        }
        List<ScoredChunk> merged = new ArrayList<>();
        double mergedLargest = 0;
        for (ScoredChunk item : scored) {
            double dense = denseScores.getOrDefault(item.chunk().chunkId(), 0.0) / denseLargest;
            double lexical = item.score() / bm25Largest;
            double score = DENSE_WEIGHT * dense + LEXICAL_WEIGHT * lexical;
            mergedLargest = Math.max(mergedLargest, score);
            merged.add(new ScoredChunk(item.chunk(), score));
        }
        if (mergedLargest <= 0) {
            return List.of();
        }
        merged.sort(Comparator.comparingDouble(ScoredChunk::score).reversed());
        List<Day21SearchHit> hits = new ArrayList<>();
        for (ScoredChunk item : merged) {
            hits.add(new Day21SearchHit(item.chunk(), item.score(),
                    snippet(item.chunk().text())));
        }
        return hits;
    }

    private static double bm25(Day21Chunk chunk, List<String> terms, Map<String, Double> idf,
                               double avgdl) {
        double length = tokenCount(chunk);
        Map<String, Integer> counts = new HashMap<>();
        List<String> tokens = terms(chunk.text());
        for (String token : tokens) {
            counts.merge(token, 1, Integer::sum);
        }
        double score = 0;
        for (String term : terms) {
            Integer tf = counts.get(term);
            if (tf == null || tf == 0) {
                continue;
            }
            double idfValue = idf.getOrDefault(term, 0.0);
            double denominator = tf + K1 * (1 - B + B * length / avgdl);
            score += idfValue * tf * (K1 + 1) / denominator;
        }
        return score;
    }

    private static double[] documentFrequencies(List<String> terms, List<Day21Chunk> chunks) {
        double[] frequencies = new double[terms.size()];
        for (Day21Chunk chunk : chunks) {
            List<String> present = terms(chunk.text());
            for (int i = 0; i < terms.size(); i++) {
                if (present.contains(terms.get(i))) {
                    frequencies[i]++;
                }
            }
        }
        return frequencies;
    }

    private static int tokenCount(Day21Chunk chunk) {
        return chunk.text() == null ? 0 : terms(chunk.text()).size();
    }

    private static List<String> terms(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOPWORDS.contains(token)) {
                result.add(token);
            }
        }
        return result;
    }

    private record ScoredChunk(Day21Chunk chunk, double score) {
    }

    private static String snippet(String text) {
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() <= 220) {
            return compact;
        }
        return compact.substring(0, 220).trim() + "…";
    }
}