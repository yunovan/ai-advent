package com.yunovan.aiadvent.day23;

import java.util.List;

public final class Day23ControlQuestions {

    public static final int COUNT = 10;

    public static final List<Day23ControlQuestion> ALL = List.of(
            new Day23ControlQuestion("q01", "Что такое эмбеддинги документов и как они получаются?",
                    List.of("эмбеддинг", "вектор", "n-грамм"),
                    List.of("embeddings.md")),
            new Day23ControlQuestion("q02", "Зачем дробить документы на чанки и как это делать?",
                    List.of("чанк", "фрагмент", "окно"),
                    List.of("chunking.md")),
            new Day23ControlQuestion("q03", "Какие коды ошибок JSON-RPC определены для MCP-сервера и что означает код −32601?",
                    List.of("jsonrpc", "ошибок", "32601"),
                    List.of("mcp.md")),
            new Day23ControlQuestion("q04", "Как устроена долговременная память агента?",
                    List.of("память", "долговременн"),
                    List.of("memory.md")),
            new Day23ControlQuestion("q05", "Из каких этапов состоит RAG-пайплайн?",
                    List.of("поиск", "контекст", "чанк"),
                    List.of("rag-pipeline.md")),
            new Day23ControlQuestion("q06", "Как работает метод tokenize в TokenizerSample и во что превращает текст?",
                    List.of("токенизатор", "токен", "tokenize"),
                    List.of("TokenizerSample.java")),
            new Day23ControlQuestion("q07", "Как устроено асинхронное логирование в QueuedLogger?",
                    List.of("очеред", "лог", "асинхрон"),
                    List.of("QueuedLogger.java")),
            new Day23ControlQuestion("q08", "Что такое проект AI Advent?",
                    List.of("advent", "проект"),
                    List.of("README.md")),
            new Day23ControlQuestion("q09", "На каком порту работает MCP-сервер оркестратора?",
                    List.of("9093", "оркестратор", "порт"),
                    List.of("README.md")),
            new Day23ControlQuestion("q10", "Какая стратегия чанкинга точнее: фиксированная или структурная?",
                    List.of("стратеги", "сравнен", "чанк"),
                    List.of("chunking.md", "rag-pipeline.md")));

    private Day23ControlQuestions() {
    }
}