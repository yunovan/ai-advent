package com.yunovan.aiadvent.day08;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.ContextualChatAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day08CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day08CliRunner.class);

    private final Day08TokenService service;
    private final ApplicationContext applicationContext;

    public Day08CliRunner(Day08TokenService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"8".equals(firstOption(args, "day"))) {
            log.info("Day 8 web UI: http://localhost:8080/day8.html  |  API: POST /api/day8/chat");
            return;
        }

        String sessionId = ContextualChatAgent.normalize(firstOption(args, "session"));
        Long limit = parsePositiveLong(firstOption(args, "limit"));

        if (args.containsOption("reset")) {
            service.reset(sessionId);
            System.out.println("Диалог '" + sessionId + "' сброшен: история удалена.");
            System.out.println("=== CLI: перезапустите с --prompt, чтобы начать заново ===");
            maybeExit(args);
            return;
        }

        if (args.containsOption("table")) {
            printTable(sessionId);
            maybeExit(args);
            return;
        }

        String request = firstOption(args, "prompt");
        if (request == null || request.isBlank()) {
            log.info("Day 8 CLI: prompt is required (--prompt=\"...\") / --reset / --table / --limit=<токены>");
            return;
        }

        log.info("Day 8 CLI: sending request '" + sessionId + "' to token-aware agent");
        Day08ChatResponse response = service.chat(sessionId, request, limit);
        printMetrics(response);
        if (response.exceeded()) {
            System.out.println("=== ПРЕВЫШЕН ЛИМИТ ===");
            System.out.println(response.content());
        } else {
            System.out.println();
            System.out.println("=== AGENT REPLY ===");
            System.out.println(response.content());
        }

        maybeExit(args);
    }

    private void printMetrics(Day08ChatResponse response) {
        System.out.println();
        System.out.println("=== METRICS ===");
        System.out.println("request tokens (оценка):      " + response.requestTokens());
        System.out.println("history tokens (оценка):      " + response.historyTokens());
        System.out.println("prompt tokens (запрос+история): " + response.promptTokens());
        System.out.println("response tokens (оценка):     " + response.responseTokens());
        System.out.println("real prompt/completion:       "
                + response.realPromptTokens() + " / " + response.realCompletionTokens()
                + (response.realCostUsd() == null ? "" : " · $" + response.realCostUsd()));
        System.out.println("estimated turn cost:          " + response.estimatedTurnCostUsd());
        System.out.println("estimated cumulative cost:    " + response.estimatedCumulativeCostUsd());
        System.out.println("context limit:                " + response.contextLimit()
                + (response.exceeded() ? " · превышен!" : ""));
        System.out.println("messages in store:            " + response.messageCount());
    }

    private void printTable(String sessionId) {
        Day08GrowthReport report = service.metrics(sessionId);
        System.out.println();
        System.out.println("=== TOKEN GROWTH: '" + report.sessionId() + "' (limit " + report.contextLimit() + ") ===");
        System.out.println("# | prompt tok | resp tok | cumulative tok | turn cost | cumulative cost");
        for (Day08GrowthTurn turn : report.turns()) {
            System.out.println(turn.turn()
                    + " | " + turn.promptTokens()
                    + " | " + turn.responseTokens()
                    + " | " + turn.cumulativeTokens()
                    + " | $ " + turn.turnCostUsd()
                    + " | $ " + turn.cumulativeCostUsd());
        }
        System.out.println("total tokens in dialog: " + report.totalTokens());
        System.out.println("total estimated cost:   $ " + report.totalCostUsd());
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