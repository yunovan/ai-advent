package com.yunovan.aiadvent.day34;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class Day34InvariantRules {

    private static final Pattern EMPTY_CATCH = Pattern.compile(
            "catch\\s*\\([^)]*\\)\\s*\\{\\s*}", Pattern.MULTILINE);
    private static final int MAX_LINE = 160;

    public record RuleInfo(String name, String severity, String description) {
    }

    private static final List<RuleInfo> RULES = List.of(
            new RuleInfo("no-system-out", "medium",
                    "В коде не должно быть отладочного вывода через System.out/System.err"),
            new RuleInfo("no-print-stack-trace", "high",
                    "printStackTrace() недопустим — используйте логгер"),
            new RuleInfo("no-empty-catch", "high",
                    "Пустой catch скрывает ошибки — обработайте или пробросьте исключение"),
            new RuleInfo("no-todo", "low",
                    "Не оставляйте TODO/FIXME в готовом коде"),
            new RuleInfo("no-tabs", "low",
                    "Отступы должны быть пробелами, без символов табуляции"),
            new RuleInfo("line-length", "low",
                    "Строка длиннее " + MAX_LINE + " символов затрудняет чтение"),
            new RuleInfo("trailing-whitespace", "low",
                    "В конце строк не должно быть пробелов"),
            new RuleInfo("final-newline", "low",
                    "Файл должен заканчиваться переводом строки"));

    private Day34InvariantRules() {
    }

    public static List<RuleInfo> describe() {
        return RULES;
    }

    public static List<Day34Finding> analyze(Map<String, String> files) {
        List<Day34Finding> findings = new ArrayList<>();
        for (Map.Entry<String, String> entry : files.entrySet()) {
            findings.addAll(analyze(entry.getKey(), entry.getValue()));
        }
        return findings;
    }

    public static List<Day34Finding> analyze(String file, String content) {
        List<Day34Finding> findings = new ArrayList<>();
        if (content == null) {
            return findings;
        }
        String lowerName = file.toLowerCase(Locale.ROOT);
        boolean code = lowerName.endsWith(".java") || lowerName.endsWith(".kt")
                || lowerName.endsWith(".kts") || lowerName.endsWith(".js")
                || lowerName.endsWith(".ts");
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;
            if (line.contains("System.out.") || line.contains("System.err.")) {
                findings.add(Day34Finding.of("medium", "no-system-out", file, lineNumber,
                        "Отладочный вывод: " + trim(line)));
            }
            if (line.contains("printStackTrace")) {
                findings.add(Day34Finding.of("high", "no-print-stack-trace", file, lineNumber,
                        "printStackTrace в " + file + ":" + lineNumber));
            }
            if (line.matches(".*\\bTODO\\b.*") || line.matches(".*\\bFIXME\\b.*")) {
                findings.add(Day34Finding.of("low", "no-todo", file, lineNumber,
                        "Незавершённая задача: " + trim(line)));
            }
            if (line.indexOf('\t') >= 0) {
                findings.add(Day34Finding.of("low", "no-tabs", file, lineNumber, "Символ табуляции"));
            }
            if (line.length() > MAX_LINE) {
                findings.add(Day34Finding.of("low", "line-length", file, lineNumber,
                        "Длина строки " + line.length() + " > " + MAX_LINE));
            }
            if (!line.isEmpty() && (line.endsWith(" ") || line.endsWith("\t"))) {
                findings.add(Day34Finding.of("low", "trailing-whitespace", file, lineNumber,
                        "Пробел в конце строки"));
            }
        }
        for (var matcher = EMPTY_CATCH.matcher(content); matcher.find(); ) {
            findings.add(Day34Finding.of("high", "no-empty-catch", file,
                    lineNumber(content, matcher.start()), "Пустой catch блок"));
        }
        if (code && !content.isEmpty() && !content.endsWith("\n")) {
            findings.add(Day34Finding.of("low", "final-newline", file, lines.length,
                    "Нет перевода строки в конце файла"));
        }
        return findings;
    }

    private static int lineNumber(String content, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String trim(String line) {
        String value = line.strip();
        return value.length() <= 120 ? value : value.substring(0, 120) + "…";
    }
}
