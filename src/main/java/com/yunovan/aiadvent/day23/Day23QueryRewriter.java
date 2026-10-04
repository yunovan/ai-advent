package com.yunovan.aiadvent.day23;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Day23QueryRewriter {

    public record Rewrite(String original, String rewritten, List<String> expansions) {
    }

    private record Rule(String needle, String expansion) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule("mcp", "mcp протокол json-rpc сервер инструменты"),
            new Rule("эмбеддинг", "эмбеддинг вектор признак n-грамм документ"),
            new Rule("вектор", "вектор признак эмбеддинг n-грамм"),
            new Rule("чанк", "чанк фрагмент разбиение окно"),
            new Rule("пайплайн", "пайплайн этапы поиск контекст генерация"),
            new Rule("этап", "этап шаг пайплайн поиск"),
            new Rule("памят", "память агента краткосрочная долговременная"),
            new Rule("json", "json-rpc rpc коды ошибки протокол"),
            new Rule("ошибк", "ошибка код json-rpc 32000 32600 32601"),
            new Rule("порт", "порт сервер 9094 9093 mcp"),
            new Rule("логирован", "логирование очередь асинхронное logger"),
            new Rule("асинхронн", "асинхронный очередь поток"),
            new Rule("tokeniz", "tokenize токенизация токен лексема текст"),
            new Rule("стратеги", "стратегия чанкинг фиксированная структурная сравнение"),
            new Rule("rag", "rag контекст пайплайн поиск"),
            new Rule("advent", "ai-advent проект ассистент"));

    private Day23QueryRewriter() {
    }

    public static Rewrite rewrite(String question) {
        String normalized = normalize(question);
        String lower = normalized.toLowerCase(Locale.ROOT);
        Set<String> kept = new LinkedHashSet<>(List.of(normalized.split("\\s+")));
        List<String> expansions = new ArrayList<>();
        for (Rule rule : RULES) {
            if (lower.contains(rule.needle())) {
                String[] words = rule.expansion().split("\\s+");
                for (String word : words) {
                    kept.add(word);
                }
                expansions.add(rule.expansion());
            }
        }
        return new Rewrite(question == null ? "" : question, String.join(" ", kept), expansions);
    }

    public static String normalize(String question) {
        if (question == null) {
            return "";
        }
        return question.trim().replaceAll("\\s+", " ");
    }

    public static boolean rewriteApplied(Rewrite rewrite) {
        return rewrite != null && !rewrite.expansions().isEmpty();
    }
}