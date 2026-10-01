package com.yunovan.samples;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Обучающий образец локального эмбеддинга: частота термов превращается в разреженное
 * представление. Совпадение слов (терминов) задаёт близость текстов.
 */
public final class EmbeddingSample {

    private EmbeddingSample() {
    }

    public static Map<String, Integer> termFrequency(List<String> tokens) {
        Map<String, Integer> frequency = new HashMap<>();
        for (String token : tokens) {
            frequency.merge(token, 1, Integer::sum);
        }
        return frequency;
    }

    public static double cosine(Map<String, Integer> first, Map<String, Integer> second) {
        Map<String, Integer> smaller = first.size() < second.size() ? first : second;
        Map<String, Integer> larger = smaller == first ? second : first;
        int dot = 0;
        int firstNorm = 0;
        int secondNorm = 0;
        for (Map.Entry<String, Integer> entry : larger.entrySet()) {
            secondNorm += entry.getValue() * entry.getValue();
            Integer other = smaller.get(entry.getKey());
            if (other != null) {
                dot += entry.getValue() * other;
            }
            firstNorm += other == null ? 0 : other * other;
        }
        if (dot == 0) {
            return 0.0;
        }
        return dot / Math.sqrt((double) firstNorm * secondNorm);
    }

    public static void main(String[] args) {
        Map<String, Integer> doc = termFrequency(List.of("запрос", "индекс", "индекс"));
        Map<String, Integer> chunk = termFrequency(List.of("индекс", "поиск"));
        System.out.println("Косинусная близость: " + String.format("%.3f", cosine(doc, chunk)));
    }
}