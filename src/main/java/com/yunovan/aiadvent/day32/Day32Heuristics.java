package com.yunovan.aiadvent.day32;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Day32Heuristics {

    private static final Pattern SECRET = Pattern.compile(
            "(?i)(api[_-]?key|password|passwd|secret|access[_-]?token|private[_-]?key)\\s*[:=]\\s*[\"'][^\"']{6,}[\"']");
    private static final Pattern BROAD_CATCH = Pattern.compile("catch\\s*\\(\\s*(Exception|Throwable)\\b");
    private static final Pattern EMPTY_CATCH = Pattern.compile("catch\\s*\\([^)]*\\)\\s*\\{\\s*$");
    private static final Pattern TODO = Pattern.compile("(?i)\\b(TODO|FIXME|XXX|HACK)\\b");
    private static final Pattern DEBUG_OUTPUT = Pattern.compile(
            "System\\.(out|err)\\.print|printStackTrace\\(|\\.println\\(");
    private static final Pattern SLEEP = Pattern.compile("Thread\\.sleep\\(");
    private static final Pattern LEGACY_DATE = Pattern.compile("new\\s+(java\\.util\\.)?Date\\(");
    private static final Pattern SUPPRESS = Pattern.compile("@SuppressWarnings");
    private static final int LONG_LINE = 160;
    private static final int LARGE_FILE = 200;

    private Day32Heuristics() {
    }

    public static Result analyze(List<Day32FileDiff> files) {
        List<Day32Finding> bugs = new ArrayList<>();
        List<Day32Finding> architecture = new ArrayList<>();
        List<Day32Finding> recommendations = new ArrayList<>();

        int codeAdditions = 0;
        boolean codeChanged = false;
        boolean testsChanged = false;

        for (Day32FileDiff file : files) {
            String path = file.path();
            boolean code = file.code();
            if (code) {
                codeChanged = true;
                codeAdditions += file.additions();
            }
            if (isTest(path)) {
                testsChanged = true;
            }
            if ("deleted".equals(file.status()) && code) {
                architecture.add(Day32Finding.of("medium", "architecture", path, 0,
                        "код-файл удалён в PR — убедитесь, что не осталось ссылок и покрытия"));
            }
            if (file.additions() >= LARGE_FILE) {
                architecture.add(Day32Finding.of("low", "architecture", path, 0,
                        "крупное изменение (+" + file.additions()
                                + " строк) — сложно ревьюить, возможно стоит разбить PR"));
            }
            scan(file, bugs, architecture, recommendations);
        }

        if (codeChanged && !testsChanged) {
            recommendations.add(Day32Finding.of("medium", "recommendation", "", 0,
                    "изменён код, но в PR нет ни одного тестового файла — добавьте тесты"));
        }

        return new Result(List.copyOf(bugs), List.copyOf(architecture), List.copyOf(recommendations));
    }

    private static void scan(Day32FileDiff file, List<Day32Finding> bugs, List<Day32Finding> architecture,
                             List<Day32Finding> recommendations) {
        List<Day32Line> lines = file.addedLines();
        for (int i = 0; i < lines.size(); i++) {
            Day32Line line = lines.get(i);
            String text = line.text();
            String trimmed = text.trim();

            if (SECRET.matcher(text).find()) {
                bugs.add(Day32Finding.of("high", "bug", file.path(), line.number(),
                        "похоже на захардкоженный секрет или ключ — вынесите в переменную окружения"));
            }
            if (BROAD_CATCH.matcher(trimmed).find()) {
                architecture.add(Day32Finding.of("medium", "architecture", file.path(), line.number(),
                        "перехват Exception/Throwable целиком — сузьте тип и обработайте осмысленно"));
            }
            if (EMPTY_CATCH.matcher(trimmed).find() && isNextEmptyBlock(lines, i)) {
                bugs.add(Day32Finding.of("high", "bug", file.path(), line.number(),
                        "пустой catch — ошибка проглатывается, добавьте логирование или обработку"));
            }
            if (DEBUG_OUTPUT.matcher(trimmed).find() && file.code()) {
                bugs.add(Day32Finding.of("low", "bug", file.path(), line.number(),
                        "отладочный вывод (System.out/printStackTrace) — используйте логгер"));
            }
            if (SLEEP.matcher(trimmed).find()) {
                recommendations.add(Day32Finding.of("medium", "recommendation", file.path(), line.number(),
                        "Thread.sleep в коде — в продакшене подумайте об асинхронности или планировщике"));
            }
            if (LEGACY_DATE.matcher(trimmed).find()) {
                recommendations.add(Day32Finding.of("low", "recommendation", file.path(), line.number(),
                        "устаревший java.util.Date — используйте java.time"));
            }
            if (SUPPRESS.matcher(trimmed).find()) {
                recommendations.add(Day32Finding.of("low", "recommendation", file.path(), line.number(),
                        "@SuppressWarnings добавлен — убедитесь, что предупреждение не скрывает проблему"));
            }
            if (TODO.matcher(trimmed).find()) {
                recommendations.add(Day32Finding.of("low", "recommendation", file.path(), line.number(),
                        "TODO/FIXME в коде — оформите отдельной задачей, а не в этом PR"));
            }
            if (file.code() && text.length() > LONG_LINE) {
                recommendations.add(Day32Finding.of("low", "recommendation", file.path(), line.number(),
                        "очень длинная строка (" + text.length() + " символов) — разбейте для читаемости"));
            }
        }
    }

    private static boolean isNextEmptyBlock(List<Day32Line> lines, int index) {        if (index + 1 >= lines.size()) {
            return false;
        }
        String next = lines.get(index + 1).text().trim();
        return next.equals("}") || next.equals("};");
    }

    private static boolean isTest(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.contains("/test/") || lower.endsWith("test.java") || lower.endsWith("tests.java")
                || lower.endsWith("it.java") || lower.endsWith("test.kt");
    }

    public record Result(List<Day32Finding> bugs, List<Day32Finding> architecture,
                         List<Day32Finding> recommendations) {
    }
}
