package com.yunovan.aiadvent.day34;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class Day34GoalPlanner {

    private static final List<String> USAGE_KEYWORDS = List.of(
            "использован", "использует", "где использ", "usages", "usage", "find usages", "references");
    private static final List<String> ADR_KEYWORDS = List.of(
            " adr", "adr ", "architecture decision", "архитектурн решен", "решение об архитект", "decision record");
    private static final List<String> CHANGELOG_KEYWORDS = List.of(
            "changelog", "changelog", "что нового", "список изменений", "история изменений");
    private static final List<String> README_KEYWORDS = List.of(
            "readme", "описан проекта", "описание проекта", "оформить проект", "документац проекта");
    private static final List<String> INVARIANT_KEYWORDS = List.of(
            "инвариант", "проверь стиль", "стил", "линт", "lint", "проверка файлов", "правил");
    private static final List<String> UPDATE_KEYWORDS = List.of(
            "обнов", "актуализ", "синхрониз", "sync", "дополни документац", "обнови документ");

    private static final Pattern TOKEN = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");
    private static final Pattern QUOTED = Pattern.compile("[`\"'«]([^`\"'»]{2,})[`\"'»]");

    public Day34TaskType classify(String goal, String explicitTask) {
        Day34TaskType explicit = Day34TaskType.fromCode(explicitTask);
        if (explicit != null) {
            return explicit;
        }
        String text = goal == null ? "" : " " + goal.toLowerCase(Locale.ROOT) + " ";
        if (matches(text, README_KEYWORDS)) {
            return Day34TaskType.GENERATE_README;
        }
        if (matches(text, CHANGELOG_KEYWORDS)) {
            return Day34TaskType.GENERATE_CHANGELOG;
        }
        if (matches(text, ADR_KEYWORDS)) {
            return Day34TaskType.GENERATE_ADR;
        }
        if (matches(text, INVARIANT_KEYWORDS)) {
            return Day34TaskType.CHECK_INVARIANTS;
        }
        if (matches(text, UPDATE_KEYWORDS)) {
            return Day34TaskType.UPDATE_DOC;
        }
        if (matches(text, USAGE_KEYWORDS)) {
            return Day34TaskType.FIND_USAGES;
        }
        return Day34TaskType.FIND_USAGES;
    }

    public String extractTarget(String goal, String explicitTarget) {
        if (explicitTarget != null && !explicitTarget.isBlank()) {
            return explicitTarget.trim();
        }
        if (goal == null || goal.isBlank()) {
            return null;
        }
        Matcher quoted = QUOTED.matcher(goal);
        if (quoted.find()) {
            return quoted.group(1).trim();
        }
        Matcher token = TOKEN.matcher(goal);
        String candidate = null;
        while (token.find()) {
            String value = token.group();
            if (value.length() < 4 || STOPWORDS.contains(value.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (value.contains(".") || hasInternalUpper(value)) {
                return value;
            }
            if (candidate == null) {
                candidate = value;
            }
        }
        return candidate;
    }

    private static boolean hasInternalUpper(String value) {
        for (int i = 1; i < value.length(); i++) {
            if (Character.isUpperCase(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public String purpose(Day34TaskType type, String target) {
        String subject = target == null || target.isBlank() ? "проекта" : target;
        return switch (type) {
            case FIND_USAGES -> "Найти все точки использования «" + subject + "» по файлам проекта";
            case GENERATE_ADR -> "Подготовить ADR по теме «" + subject + "» на основе кода";
            case GENERATE_CHANGELOG -> "Сформировать changelog по изменениям проекта";
            case GENERATE_README -> "Собрать README/описание проекта «" + subject + "»";
            case CHECK_INVARIANTS -> "Проверить файлы проекта на соответствие инвариантам кода";
            case UPDATE_DOC -> "Обновить документацию «" + subject + "» по актуальному коду";
        };
    }

    public List<String> keywords(Day34TaskType type) {
        return switch (type) {
            case FIND_USAGES -> USAGE_KEYWORDS;
            case GENERATE_ADR -> ADR_KEYWORDS;
            case GENERATE_CHANGELOG -> CHANGELOG_KEYWORDS;
            case GENERATE_README -> README_KEYWORDS;
            case CHECK_INVARIANTS -> INVARIANT_KEYWORDS;
            case UPDATE_DOC -> UPDATE_KEYWORDS;
        };
    }

    private static boolean matches(String text, List<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.Set<String> STOPWORDS = java.util.Set.of(
            "нужно", "надо", "проект", "проекта", "файл", "файла", "файлы", "код", "кода",
            "сделай", "создай", "сгенерируй", "найди", "проверь", "обнови", "помоги",
            "please", "project", "file", "files", "code", "make", "create", "find", "check",
            "update", "generate", "this", "that", "with", "from", "into", "the",
            "usages", "usage", "references", "search", "of", "for", "and", "all");
}
