package com.yunovan.aiadvent.day10;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.dialog.DialogMemory;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day10CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day10CliRunner.class);

    private final Day10DialogService service;
    private final ApplicationContext applicationContext;

    public Day10CliRunner(Day10DialogService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"10".equals(firstOption(args, "day"))) {
            log.info("Day 10 web UI: http://localhost:8080/day10.html  |  API: POST /api/day10/dialogs");
            return;
        }

        if (args.containsOption("start")) {
            Integer window = positiveInt(firstOption(args, "window"));
            Day10StartResponse dialog = service.start(firstOption(args, "strategy"), window);
            System.out.println("Создан диалог: " + dialog.dialogId());
            System.out.println("Стратегия: " + dialog.strategy().label() + " (" + dialog.strategy().key()
                    + "), окно: " + dialog.windowSize());
            maybeExit(args);
            return;
        }

        if (args.containsOption("list")) {
            System.out.println("=== Диалоги дня 10 (сравнение стратегий) ===");
            System.out.printf("%-38s %-18s %6s %8s %10s %10s%n",
                    "dialog", "стратегия", "окно", "сообщ.", "токены", "стоимость");
            for (Day10DialogSummary dialog : service.dialogs()) {
                System.out.printf("%-38s %-18s %6d %8d %10d %10s%n",
                        dialog.dialogId(), dialog.strategy().label(), dialog.windowSize(),
                        dialog.messageCount(), dialog.totalTokens(), "$ " + dialog.totalCostUsd());
            }
            maybeExit(args);
            return;
        }

        String dialogId = firstOption(args, "dialog");
        if (dialogId == null || dialogId.isBlank()) {
            log.info("Day 10 CLI: --start [--strategy=sliding|facts|branching] [--window=N] | --list | "
                    + "--dialog=<id> --prompt=\"...\" | --fact=\"ключ: значение\" | --checkpoint | --branch | "
                    + "--switch=<branchId> | --table | --finish");
            return;
        }

        if (args.containsOption("finish")) {
            Day10FinishResponse finished = service.finish(dialogId);
            System.out.println("=== ДИАЛОГ ЗАВЕРШЁН '" + finished.dialogId() + "' ===");
            System.out.println("Итог для памяти: " + finished.summary());
            maybeExit(args);
            return;
        }

        if (args.containsOption("checkpoint")) {
            printBranches(service.checkpoint(dialogId));
            maybeExit(args);
            return;
        }

        if (args.containsOption("branch")) {
            printBranches(service.createBranch(dialogId));
            maybeExit(args);
            return;
        }

        String switchTo = firstOption(args, "switch");
        if (switchTo != null && !switchTo.isBlank()) {
            printBranches(service.switchBranch(dialogId, switchTo));
            maybeExit(args);
            return;
        }

        String fact = firstOption(args, "fact");
        if (fact != null && !fact.isBlank()) {
            int separator = fact.indexOf(':');
            if (separator <= 0) {
                log.info("Формат факта: --fact=\"ключ: значение\"");
                return;
            }
            service.addFact(dialogId, fact.substring(0, separator), fact.substring(separator + 1), true);
            printFacts(service.get(dialogId).facts());
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
            log.info("Day 10 CLI: prompt is required (--dialog=<id> --prompt=\"...\") / --finish / --table / --start / --list");
            return;
        }

        Integer window = positiveInt(firstOption(args, "window"));
        Long limit = positiveLong(firstOption(args, "limit"));
        Day10ChatResponse response = service.chat(dialogId, request, limit, window);
        printMetrics(response);
        if (response.exceeded()) {
            System.out.println("=== ПРЕВЫШЕН ЛИМИТ ===");
            System.out.println(response.content());
        } else {
            System.out.println();
            System.out.println("=== DIALOG '" + response.dialogId() + "' (" + response.messageCount()
                    + " сообщений, стратегия " + response.strategy().label()
                    + ", ветка " + response.activeBranchId() + ") ===");
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

    private void printMetrics(Day10ChatResponse response) {
        System.out.println();
        System.out.println("=== METRICS ===");
        System.out.println("strategy:                 " + response.strategy().label() + " (окно " + response.windowSize() + ")");
        System.out.println("context tokens:           " + response.contextTokens());
        System.out.println("request tokens:           " + response.requestTokens());
        System.out.println("history sent tokens:      " + response.historyTokens());
        System.out.println("history full tokens:      " + response.fullHistoryTokens());
        System.out.println("prompt tokens:            " + response.promptTokens());
        System.out.println("prompt without strategy:  " + response.fullPromptTokens());
        System.out.println("saved tokens:             " + response.savedTokens());
        System.out.println("response tokens:          " + response.responseTokens());
        System.out.println("turn cost:                " + response.estimatedTurnCostUsd());
        System.out.println("cumulative cost:          " + response.estimatedCumulativeCostUsd());
        System.out.println("cumulative full cost:     " + response.estimatedFullCumulativeCostUsd());
        System.out.println("context limit:            " + response.contextLimit()
                + (response.exceeded() ? " · превышен!" : ""));
    }

    private void printTable(String dialogId) {
        Day10GrowthReport report = service.metrics(dialogId);
        System.out.println();
        System.out.println("=== TOKEN GROWTH: '" + report.dialogId() + "' (" + report.strategy().label()
                + ", окно " + report.windowSize() + ", лимит " + report.contextLimit() + ") ===");
        System.out.println("# | prompt | response | cumulative | turn cost | cumulative cost");
        for (Day10GrowthTurn turn : report.turns()) {
            System.out.println(turn.turn()
                    + " | " + turn.promptTokens()
                    + " | " + turn.responseTokens()
                    + " | " + turn.cumulativeTokens()
                    + " | $ " + turn.turnCostUsd()
                    + " | $ " + turn.cumulativeCostUsd());
        }
        System.out.println("total tokens (strategy): " + report.totalTokens());
        System.out.println("total tokens (no strategy): " + report.fullTotalTokens());
        System.out.println("total cost (strategy):   $ " + report.totalCostUsd());
        System.out.println("total cost (no strategy): $ " + report.fullTotalCostUsd());
        System.out.println("facts: " + report.factsCount() + ", branches: " + report.branchCount()
                + ", active: " + report.activeBranchId());
    }

    private void printBranches(Day10DialogInfo info) {
        System.out.println("Ветки диалога '" + info.dialogId() + "':");
        for (Day10Branch branch : info.branches()) {
            System.out.println((branch.id().equals(info.activeBranchId()) ? "  * " : "    ")
                    + "[" + branch.id() + "] " + branch.name()
                    + " · сообщений: " + branch.messageCount()
                    + " · checkpoint: " + branch.checkpointAt()
                    + (branch.parentId() == null ? "" : " · от ветки " + branch.parentId()));
        }
    }

    private void printFacts(List<Day10Fact> facts) {
        System.out.println("Факты:");
        if (facts.isEmpty()) {
            System.out.println("  (пусто)");
            return;
        }
        for (Day10Fact fact : facts) {
            System.out.println("  " + (fact.active() ? "[x]" : "[ ]") + " " + fact.display());
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

    private static Integer positiveInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
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