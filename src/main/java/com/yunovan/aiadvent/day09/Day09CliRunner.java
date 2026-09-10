package com.yunovan.aiadvent.day09;

import com.yunovan.aiadvent.agent.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day09CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day09CliRunner.class);

    private final Day09DialogService service;
    private final ApplicationContext applicationContext;

    public Day09CliRunner(Day09DialogService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"9".equals(firstOption(args, "day"))) {
            log.info("Day 9 web UI: http://localhost:8080/day9.html  |  API: POST /api/day9/dialogs");
            return;
        }

        Long limit = parsePositiveLong(firstOption(args, "limit"));
        Boolean compress = args.containsOption("no-compress") ? false : null;

        if (args.containsOption("start")) {
            Day09StartResponse dialog = service.start();
            System.out.println("Создан диалог: " + dialog.dialogId());
            System.out.println("Память агента:");
            printMemory(dialog.memory());
            maybeExit(args);
            return;
        }

        if (args.containsOption("list")) {
            System.out.println("=== Завершённые диалоги (память агента) ===");
            for (Day09DialogSummary dialog : service.dialogs()) {
                System.out.println("- [" + dialog.dialogId() + "] от " + dialog.finishedAt()
                        + " (" + dialog.messageCount() + " сообщений)");
                System.out.println("  " + dialog.summary());
            }
            maybeExit(args);
            return;
        }

        String dialogId = firstOption(args, "dialog");
        if (dialogId == null || dialogId.isBlank()) {
            log.info("Day 9 CLI: --start | --list | --dialog=<id> --prompt=\"...\" | --dialog=<id> --finish"
                    + " | --dialog=<id> --table | --limit=<токены> | --no-compress");
            return;
        }

        if (args.containsOption("finish")) {
            Day09FinishResponse finished = service.finish(dialogId);
            System.out.println("=== ДИАЛОГ ЗАВЕРШЁН '" + finished.dialogId() + "' ===");
            System.out.println("Итог для памяти: " + finished.summary());
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
            log.info("Day 9 CLI: prompt is required (--dialog=<id> --prompt=\"...\") / --finish / --start / --list / --table");
            return;
        }

        log.info("Day 9 CLI: sending request to dialog '" + dialogId + "'"
                + (compress != null && !compress ? " (без сжатия)" : " (со сжатием)"));
        Day09ChatResponse response = service.chat(dialogId, request, limit, compress);
        printMetrics(response);
        if (response.exceeded()) {
            System.out.println("=== ПРЕВЫШЕН ЛИМИТ ===");
            System.out.println(response.content());
        } else {
            System.out.println();
            System.out.println("=== DIALOG '" + response.dialogId() + "' (" + response.messageCount() + " сообщений) ===");
            for (ConversationMessage message : response.history()) {
                System.out.println();
                System.out.println("[" + message.role() + "]");
                System.out.println(message.content());
            }
            System.out.println();
            System.out.println("=== AGENT REPLY ===");
            System.out.println(response.content());
            System.out.println("model: " + response.model() + " · " + response.elapsedMs()
                    + " мс · сжатие: " + (response.compression() ? "вкл" : "выкл")
                    + " · сжато сообщений: " + response.historySummaryCount()
                    + " · сэкономлено ~" + response.savedTokens() + " токенов");
        }
        System.out.println("===================");

        maybeExit(args);
    }

    private void printMetrics(Day09ChatResponse response) {
        System.out.println();
        System.out.println("=== METRICS ===");
        System.out.println("compression:                   " + (response.compression() ? "on" : "off")
                + " (summary covering " + response.historySummaryCount() + " messages)");
        System.out.println("context tokens (суммарно):     " + response.contextTokens()
                + (response.historySummaryTokens() > 0 ? " (из них summary ~" + response.historySummaryTokens() + ")" : ""));
        System.out.println("request tokens (оценка):       " + response.requestTokens());
        System.out.println("history tokens (отправлено):    " + response.historyTokens());
        System.out.println("history tokens (полная):        " + response.fullHistoryTokens());
        System.out.println("prompt tokens (отправлено):     " + response.promptTokens());
        System.out.println("prompt tokens (без сжатия):     " + response.fullPromptTokens());
        System.out.println("saved tokens:                  " + response.savedTokens());
        System.out.println("response tokens (оценка):      " + response.responseTokens());
        System.out.println("real prompt/completion:         "
                + response.realPromptTokens() + " / " + response.realCompletionTokens()
                + (response.realCostUsd() == null ? "" : " · $" + response.realCostUsd()));
        System.out.println("estimated turn cost:            " + response.estimatedTurnCostUsd());
        System.out.println("estimated cumulative cost:      " + response.estimatedCumulativeCostUsd());
        System.out.println("estimated full cumulative cost: " + response.estimatedFullCumulativeCostUsd());
        System.out.println("context limit:                  " + response.contextLimit()
                + (response.exceeded() ? " · превышен!" : ""));
        System.out.println("messages in dialog:             " + response.messageCount());
    }

    private void printTable(String dialogId) {
        Day09GrowthReport report = service.metrics(dialogId);
        System.out.println();
        System.out.println("=== TOKEN GROWTH: '" + report.dialogId() + "' (limit " + report.contextLimit() + ") ===");
        System.out.println("# | sent prompt | full prompt | resp | cum sent | cum full | turn cost | cum cost | cum full cost");
        for (Day09GrowthTurn turn : report.turns()) {
            System.out.println(turn.turn()
                    + " | " + turn.promptTokens()
                    + " | " + turn.fullPromptTokens()
                    + " | " + turn.responseTokens()
                    + " | " + turn.cumulativeTokens()
                    + " | " + turn.cumulativeFullTokens()
                    + " | $ " + turn.turnCostUsd()
                    + " | $ " + turn.cumulativeCostUsd()
                    + " | $ " + turn.cumulativeFullCostUsd());
        }
        System.out.println("total tokens (sent):  " + report.totalTokens());
        System.out.println("total tokens (full):   " + report.fullTotalTokens());
        System.out.println("saved tokens:          " + report.savedTokens());
        System.out.println("total cost (sent):     $ " + report.totalCostUsd());
        System.out.println("total cost (full):     $ " + report.fullTotalCostUsd());
    }

    private void printMemory(java.util.List<com.yunovan.aiadvent.agent.dialog.DialogMemory> memory) {
        if (memory.isEmpty()) {
            System.out.println("  (пусто — прошлых диалогов ещё нет)");
            return;
        }
        for (com.yunovan.aiadvent.agent.dialog.DialogMemory item : memory) {
            System.out.println("  - [" + item.dialogId() + "] " + item.summary());
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

    private static Long parsePositiveLong(String value) {
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