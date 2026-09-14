package com.yunovan.aiadvent.day11;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day11CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day11CliRunner.class);

    private final Day11DialogService service;
    private final ApplicationContext applicationContext;

    public Day11CliRunner(Day11DialogService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"11".equals(firstOption(args, "day"))) {
            log.info("Day 11 web UI: http://localhost:8080/day11.html  |  API: POST /api/day11/dialogs");
            return;
        }

        if (args.containsOption("start")) {
            Day11StartResponse started = service.start();
            System.out.println("Создан диалог: " + started.dialogId());
            for (Day11MemoryLayer layer : started.layers()) {
                System.out.println("Слой: " + layer.key() + " — " + layer.label());
            }
            maybeExit(args);
            return;
        }

        if (args.containsOption("list")) {
            System.out.println("=== Диалоги дня 11 (память по слоям) ===");
            List<Day11DialogInfo> dialogs = service.dialogs().stream()
                    .map(dialog -> service.get(dialog.id()))
                    .toList();
            for (Day11DialogInfo dialog : dialogs) {
                System.out.println(dialog.dialogId()
                        + "  сообщений: " + dialog.messageCount()
                        + "  кандидатов: " + dialog.candidates().size()
                        + "  рабочая: " + dialog.working().size()
                        + "  долговременная: " + dialog.longTerm().size()
                        + (dialog.finishedAt() == null ? "" : "  завершён"));
            }
            maybeExit(args);
            return;
        }

        String dialogId = firstOption(args, "dialog");
        if (dialogId == null || dialogId.isBlank()) {
            log.info("Day 11 CLI: --start | --list | "
                    + "--dialog=<id> --prompt=\"...\" [--limit=N] | --remember=\"ключ: значение\" --layer=working | "
                    + "--promote=\"ключ\" --target=long-term | --decide=\"решение\" | "
                    + "--forget --layer=short-term --key=\"ключ\" | --table | --finish");
            return;
        }

        if (args.containsOption("finish")) {
            Day11FinishResponse finished = service.finish(dialogId);
            System.out.println("=== ДИАЛОГ ЗАВЕРШЁН '" + finished.dialogId() + "' ===");
            System.out.println("Итог для долговременной памяти: " + finished.summary());
            System.out.println("Записей в долговременной памяти: " + finished.longTermEntryCount());
            maybeExit(args);
            return;
        }

        if (args.containsOption("forget")) {
            String layer = firstOption(args, "layer");
            String key = firstOption(args, "key");
            if (layer == null || layer.isBlank() || key == null || key.isBlank()) {
                log.info("Формат: --dialog=<id> --forget --layer=short-term --key=\"ключ\"");
                return;
            }
            Day11DialogInfo info = service.forget(dialogId, layer, key);
            printInfo(info);
            maybeExit(args);
            return;
        }

        String remember = firstOption(args, "remember");
        if (remember != null && !remember.isBlank()) {
            int separator = remember.indexOf(':');
            if (separator <= 0) {
                log.info("Формат: --dialog=<id> --remember=\"ключ: значение\" --layer=working");
                return;
            }
            String layer = firstOption(args, "layer");
            Day11DialogInfo info = service.remember(
                    dialogId,
                    remember.substring(0, separator).trim(),
                    remember.substring(separator + 1).trim(),
                    layer);
            printInfo(info);
            maybeExit(args);
            return;
        }

        String promoteKey = firstOption(args, "promote");
        if (promoteKey != null && !promoteKey.isBlank()) {
            String target = firstOption(args, "target");
            Day11DialogInfo info = service.promote(dialogId, promoteKey.trim(), target);
            printInfo(info);
            maybeExit(args);
            return;
        }

        String decision = firstOption(args, "decide");
        if (decision != null && !decision.isBlank()) {
            Day11DialogInfo info = service.decide(dialogId, decision);
            printInfo(info);
            maybeExit(args);
            return;
        }

        if (args.containsOption("table")) {
            printTable(dialogId);
            maybeExit(args);
            return;
        }

        String request = firstOption(args, "prompt");
        if (request == null || request.isBlank()) {
            Day11DialogInfo info = service.get(dialogId);
            printInfo(info);
            maybeExit(args);
            return;
        }

        Long limit = positiveLong(firstOption(args, "limit"));
        Day11ChatResponse response = service.chat(dialogId, request, limit);
        printMetrics(response);
        if (response.exceeded()) {
            System.out.println("=== ПРЕВЫШЕН ЛИМИТ ===");
            System.out.println(response.content());
        } else {
            System.out.println();
            System.out.println("=== DIALOG '" + response.dialogId() + "' (" + response.messageCount()
                    + " сообщений) ===");
            for (ConversationMessage message : response.history()) {
                System.out.println();
                System.out.println("[" + message.role() + "]");
                System.out.println(message.content());
            }
            System.out.println();
            System.out.println("=== AGENT REPLY ===");
            System.out.println(response.content());
            System.out.println("model: " + response.model() + " · " + response.elapsedMs() + " мс");
        }
        System.out.println("===================");
        maybeExit(args);
    }

    private void printMetrics(Day11ChatResponse response) {
        System.out.println();
        System.out.println("=== METRICS ===");
        System.out.println("context tokens:     " + response.contextTokens());
        System.out.println("request tokens:     " + response.requestTokens());
        System.out.println("short-term tokens:  " + response.shortTermTokens());
        System.out.println("working tokens:     " + response.workingTokens());
        System.out.println("long-term tokens:   " + response.longTermTokens());
        System.out.println("response tokens:    " + response.responseTokens());
        System.out.println("prompt tokens:      " + response.promptTokens());
        System.out.println("context limit:      " + response.contextLimit()
                + (response.exceeded() ? " · превышен!" : ""));
        System.out.println("candidates:         " + response.candidates().size());
        System.out.println("working entries:    " + response.working().size());
        System.out.println("long-term entries:  " + response.longTerm().size());
    }

    private void printTable(String dialogId) {
        Day11MetricsReport report = service.metrics(dialogId);
        System.out.println();
        System.out.println("=== TOKEN GROWTH: '" + report.dialogId() + "' (лимит " + report.contextLimit()
                + ", цена in $" + report.inputPrice() + "/M, out $" + report.outputPrice() + "/M) ===");
        System.out.println("# | prompt | cumulative | cumulative-full | turn cost | cumulative cost");
        for (Day11MetricsTurn turn : report.turns()) {
            System.out.println(turn.turn()
                    + " | " + turn.promptTokens()
                    + " | " + turn.cumulativeTokens()
                    + " | " + turn.cumulativeFullTokens()
                    + " | $ " + turn.turnCost()
                    + " | $ " + turn.cumulativeCost());
        }
        System.out.println("total tokens:        " + report.totalTokens());
        System.out.println("total tokens (full): " + report.fullTotalTokens());
        System.out.println("total cost:          $ " + report.totalCostUsd());
        System.out.println("total cost (full):   $ " + report.fullTotalCostUsd());
        System.out.println("messages: " + report.messageCount()
                + ", working: " + report.workingCount()
                + ", long-term: " + report.longTermCount());
    }

    private void printInfo(Day11DialogInfo info) {
        System.out.println("=== ДИАЛОГ '" + info.dialogId() + "' (сообщений: " + info.messageCount()
                + (info.finishedAt() == null ? "" : ", завершён") + ") ===");
        System.out.println("--- Краткосрочная (кандидаты) ---");
        printEntries(info.candidates());
        System.out.println("--- Рабочая ---");
        printEntries(info.working());
        System.out.println("--- Долговременная ---");
        printEntries(info.longTerm());
    }

    private static void printEntries(List<Day11MemoryEntry> entries) {
        if (entries.isEmpty()) {
            System.out.println("  (пусто)");
            return;
        }
        for (Day11MemoryEntry entry : entries) {
            System.out.println("  " + entry.display()
                    + "  [" + entry.source()
                    + (entry.isCandidate() ? ", кандидат" : "") + "]");
        }
    }

    private void maybeExit(ApplicationArguments args) {
        if (args.containsOption("cli")) {
            int code = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(code);
        }
    }

    private static String firstOption(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }

    private static Long positiveLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}