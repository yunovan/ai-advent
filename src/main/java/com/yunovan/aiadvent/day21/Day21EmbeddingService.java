package com.yunovan.aiadvent.day21;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class Day21EmbeddingService {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final int MAX_NGRAM = 3;

    private final int dimensions;

    public Day21EmbeddingService(Day21Properties properties) {
        this.dimensions = properties.dimensions();
    }

    public Day21Embedding embed(String text) {
        float[] vector = new float[dimensions];
        List<String> tokens = tokens(text);
        if (tokens.isEmpty()) {
            return new Day21Embedding(vector);
        }
        for (int size = 1; size <= MAX_NGRAM && size <= tokens.size(); size++) {
            for (int i = 0; i + size <= tokens.size(); i++) {
                StringBuilder ngram = new StringBuilder();
                for (int j = i; j < i + size; j++) {
                    if (j > i) {
                        ngram.append(' ');
                    }
                    ngram.append(tokens.get(j));
                }
                addFeature(vector, ngram.toString());
            }
        }
        normalize(vector);
        return new Day21Embedding(vector);
    }

    public static double cosine(Day21Embedding a, Day21Embedding b) {
        if (a == null || b == null || a.values().length == 0 || b.values().length == 0) {
            return 0.0;
        }
        int length = Math.min(a.values().length, b.values().length);
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < length; i++) {
            double av = a.values()[i];
            double bv = b.values()[i];
            dot += av * bv;
            normA += av * av;
            normB += bv * bv;
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static List<String> tokens(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private void addFeature(float[] vector, String ngram) {
        int h1 = ngram.hashCode();
        int h2 = (int) (31L * h1 + 7L);
        int sign = (h1 & 1) == 0 ? 1 : -1;
        for (int i = 1; i <= 4; i++) {
            long raw = ((long) h1 * i + (long) h2 * 31L) & 0x7fffffffL;
            int idx = (int) (raw % dimensions);
            vector[idx] += sign;
        }
    }

    private static void normalize(float[] vector) {
        double norm = 0.0;
        for (float value : vector) {
            norm += (double) value * value;
        }
        if (norm <= 0.0) {
            return;
        }
        double factor = Math.sqrt(norm);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / factor);
        }
    }
}