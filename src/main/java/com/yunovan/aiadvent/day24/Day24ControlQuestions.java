package com.yunovan.aiadvent.day24;

import java.util.List;

public final class Day24ControlQuestions {

    public static final int COUNT = 10;

    public static final List<Day24ControlQuestion> ALL = List.of(
            new Day24ControlQuestion("q01", "Что такое эмбеддинги документов и как они получаются?",
                    List.of("эмбеддинг", "вектор", "n-грамм"),
                    List.of("embeddings.md")),
            new Day24ControlQuestion("q02", "Зачем дробить документы на чанки и как это делать?",
                    List.of("чанк", "фрагмент", "окно"),
                    List.of("chunking.md")),
            new Day24ControlQuestion("q03", "Какие коды ошибок JSON-RPC определены для MCP-сервера и что означает код −32601?",
                    List.of("jsonrpc", "ошибок", "32601"),
                    List.of("mcp.md")),
            new Day24ControlQuestion("q04", "Как устроена долговременная память агента?",
                    List.of("память", "долговременн"),
                    List.of("memory.md")),
            new Day24ControlQuestion("q05", "Из каких этапов состоит RAG-пайплайн?",
                    List.of("поиск", "контекст", "чанк"),
                    List.of("rag-pipeline.md")),
            new Day24ControlQuestion("q06", "Как работает метод tokenize в TokenizerSample и во что превращает текст?",
                    List.of("токенизатор", "токен", "tokenize"),
                    List.of("TokenizerSample.java")),
            new Day24ControlQuestion("q07", "Как устроено асинхронное логирование в QueuedLogger?",
                    List.of("очеред", "лог", "асинхрон"),
                    List.of("QueuedLogger.java")),
            new Day24ControlQuestion("q08", "Что такое проект AI Advent?",
                    List.of("advent", "проект"),
                    List.of("README.md")),
            new Day24ControlQuestion("q09", "На каком порту работает MCP-сервер оркестратора?",
                    List.of("9093", "оркестратор", "порт"),
                    List.of("README.md")),
            new Day24ControlQuestion("q10", "Какая стратегия чанкинга точнее: фиксированная или структурная?",
                    List.of("стратеги", "сравнен", "чанк"),
                    List.of("chunking.md", "rag-pipeline.md")));

    public static final List<Day24WeakQuestion> WEAK = List.of(
            new Day24WeakQuestion("w01", "Какая сегодня погода в Москве?"),
            new Day24WeakQuestion("w02", "Который час в Токио?"));

    private Day24ControlQuestions() {
    }
}