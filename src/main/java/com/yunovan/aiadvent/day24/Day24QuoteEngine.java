package com.yunovan.aiadvent.day24;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Day24QuoteEngine {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?…])\\s+");

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

    private Day24QuoteEngine() {
    }

    public static List<Day24Quote> extract(List<Day21SearchHit> hits, String query,
                                           Day24Properties properties) {
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }
        List<String> queryTerms = terms(query);
        int perSource = properties.quotesPerSource();
        int limit = properties.topKAfter() * perSource;
        List<Day24Quote> quotes = new ArrayList<>();
        for (Day21SearchHit hit : hits) {
            Day21Chunk chunk = hit.chunk();
            String section = section(chunk);
            List<RankedSentence> ranked = new ArrayList<>();
            for (String sentence : sentences(chunk.text())) {
                String clean = sentence.trim();
                if (clean.length() < properties.quoteMinChars()) {
                    continue;
                }
                int matched = matched(queryTerms, clean);
                if (matched == 0) {
                    continue;
                }
                ranked.add(new RankedSentence(clean, matched));
            }
            ranked.sort(Comparator.comparingInt(RankedSentence::matched).reversed());
            int added = 0;
            for (RankedSentence sentence : ranked) {
                if (added >= perSource || quotes.size() >= limit) {
                    break;
                }
                quotes.add(new Day24Quote(chunk.source(), section, chunk.chunkId(),
                        sentence.text(), hit.score(),
                        sentence.matched()));
                added++;
            }
            if (quotes.size() >= limit) {
                break;
            }
        }
        return quotes;
    }

    public static int lexicalEvidence(List<Day21SearchHit> hits, String query) {
        List<String> terms = terms(query);
        if (hits == null || hits.isEmpty() || terms.isEmpty()) {
            return 0;
        }
        int evidence = 0;
        for (Day21SearchHit hit : hits) {
            String lower = hit.chunk().text() == null ? "" : hit.chunk().text().toLowerCase(Locale.ROOT);
            for (String term : terms) {
                if (lower.contains(term)) {
                    evidence++;
                }
            }
        }
        return evidence;
    }

    public static double supportCoverage(String answer, List<Day24Quote> quotes) {
        if (answer == null || answer.isBlank()
                || quotes == null || quotes.isEmpty()) {
            return 0;
        }
        List<String> terms = terms(answer);
        if (terms.isEmpty()) {
            return 0;
        }
        StringBuilder evidence = new StringBuilder();
        for (Day24Quote quote : quotes) {
            evidence.append(quote.text()).append(' ');
        }
        String evidenceText = evidence.toString().toLowerCase(Locale.ROOT);
        int matched = 0;
        for (String term : terms) {
            if (evidenceText.contains(term)) {
                matched++;
            }
        }
        return (double) matched / terms.size();
    }

    static List<String> terms(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOPWORDS.contains(token) && token.length() >= 3) {
                result.add(token);
            }
        }
        return result;
    }

    private static int matched(List<String> queryTerms, String sentence) {
        String lower = sentence.toLowerCase(Locale.ROOT);
        int matched = 0;
        for (String term : queryTerms) {
            if (lower.contains(term)) {
                matched++;
            }
        }
        return matched;
    }

    private static List<String> sentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Set<String> unique = new LinkedHashSet<>();
        String[] parts = SENTENCE.split(text.trim());
        for (String part : parts) {
            String clean = part.replaceAll("\\s+", " ").trim();
            if (!clean.isEmpty()) {
                unique.add(clean);
            }
        }
        return new ArrayList<>(unique);
    }

    private static String section(Day21Chunk chunk) {
        return chunk.section() == null || chunk.section().isEmpty()
                ? chunk.title() : chunk.section();
    }

    private record RankedSentence(String text, int matched) {
    }
}