package com.yunovan.aiadvent.day25;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Day25MemoryExtractor {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

    private static final List<String> GOAL_MARKERS = List.of(
            "моя цель", "цель диалога", "цель:", "задача:", "задача диалога",
            "хочу понять", "хочу разобраться", "мне нужно", "нужно понять",
            "помоги разобраться", "объясни", "расскажи", "покажи", "изучи");

    private static final List<String> CONSTRAINT_MARKERS = List.of(
            "не используй", "не используйте", "не надо", "не нужно", "без ",
            "только ", "ограничение", "ограничения", "не больше", "не меньше",
            "минимум", "максимум", "используй только", "не включай");

    private static final Set<String> STOPWORDS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
            "и", "в", "во", "не", "что", "он", "на", "я", "с", "со", "как", "а", "то",
            "все", "она", "так", "его", "но", "да", "ты", "к", "у", "же", "вы", "за",
            "по", "при", "об", "о", "из", "от", "для", "или", "это", "их", "быть",
            "чем", "если", "мы", "кто", "какой", "какая", "какие", "зачем", "уже",
            "можно", "должен", "эта", "эти", "того", "всех", "будет", "только",
            "чтобы", "который", "которая", "которые", "этот", "какое", "также",
            "понять", "понятно", "спасибо", "спасибочки", "окей", "хорошо", "давай",
            "ещё", "еще", "всё", "всё", "мочь", "нужно", "надо", "есть", "мой",
            "моя", "мне", "меня", "ещё")));

    private Day25MemoryExtractor() {
    }

    public static String goal(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        for (String marker : GOAL_MARKERS) {
            int index = lower.indexOf(marker);
            if (index >= 0) {
                String tail = message.substring(index + marker.length()).replaceFirst("^\\s*[-—:,.\\u2014]*\\s*", "");
                if (!tail.isBlank()) {
                    return tail.trim();
                }
            }
        }
        return null;
    }

    public static List<String> constraints(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String marker : CONSTRAINT_MARKERS) {
            int index = lower.indexOf(marker);
            if (index < 0) {
                continue;
            }
            String tail = message.substring(index).trim();
            if (!tail.isBlank() && !result.contains(tail)) {
                result.add(tail);
            }
        }
        return result;
    }

    public static List<String> terms(String message, int limit) {
        List<String> result = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(message.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (STOPWORDS.contains(token) || token.length() < 4) {
                continue;
            }
            if (!result.contains(token)) {
                result.add(token);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    public static boolean retainsGoal(Day25TaskMemory memory, List<String> goalTerms) {
        if (memory == null || memory.goal() == null || memory.goal().isBlank()) {
            return false;
        }
        if (goalTerms == null || goalTerms.isEmpty()) {
            return true;
        }
        String goal = memory.goal().toLowerCase(Locale.ROOT);
        String terms = String.join(" ", memory.terms()).toLowerCase(Locale.ROOT);
        for (String term : goalTerms) {
            String needle = term.toLowerCase(Locale.ROOT);
            if (!goal.contains(needle) && !terms.contains(needle)) {
                return false;
            }
        }
        return true;
    }

    public static boolean keepsConstraints(Day25TaskMemory memory, List<String> expected) {
        if (expected == null || expected.isEmpty()) {
            return true;
        }
        List<String> constraints = memory == null ? List.of() : memory.constraints();
        for (String constraint : expected) {
            boolean found = false;
            for (String stored : constraints) {
                if (stored.toLowerCase(Locale.ROOT).contains(constraint.toLowerCase(Locale.ROOT))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
