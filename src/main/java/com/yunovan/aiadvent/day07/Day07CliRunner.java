package com.yunovan.aiadvent.day07;

import com.yunovan.aiadvent.agent.ConversationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day07CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day07CliRunner.class);

    private final Day07DialogService service;
    private final ApplicationContext applicationContext;

    public Day07CliRunner(Day07DialogService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"7".equals(firstOption(args, "day"))) {
            log.info(
                    "Day 7 web UI: http://localhost:8080/day7.html  |  API: POST /api/day7/dialogs");
            return;
        }

        if (args.containsOption("start")) {
            Day07StartResponse dialog = service.start();
            System.out.println("Создан диалог: " + dialog.dialogId());
            System.out.println("Память агента:");
            printMemory(dialog.memory());
            maybeExit(args);
            return;
        }

        if (args.containsOption("list")) {
            System.out.println("=== Завершённые диалоги (память агента) ===");
            for (Day07DialogSummary dialog : service.dialogs()) {
                System.out.println("- [" + dialog.dialogId() + "] от " + dialog.finishedAt()
                        + " (" + dialog.messageCount() + " сообщений)");
                System.out.println("  " + dialog.summary());
            }
            maybeExit(args);
            return;
        }

        String dialogId = firstOption(args, "dialog");
        if (dialogId == null || dialogId.isBlank()) {
            log.info("Day 7 CLI: --start | --list | --dialog=<id> --prompt=\"...\" | --dialog=<id> --finish");
            return;
        }

        if (args.containsOption("finish")) {
            Day07FinishResponse finished = service.finish(dialogId);
            System.out.println("=== ДИАЛОГ ЗАВЕРШЁН '" + finished.dialogId() + "' ===");
            System.out.println("Итог для памяти: " + finished.summary());
            maybeExit(args);
            return;
        }

        String request = firstOption(args, "prompt");
        if (request == null || request.isBlank()) {
            log.info("Day 7 CLI: prompt is required (--dialog=<id> --prompt=\"...\") / --finish / --start / --list");
            return;
        }

        log.info("Day 7 CLI: sending request to dialog '" + dialogId + "'");
        Day07ChatResponse response = service.chat(dialogId, request);
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
                + " мс · диалог сохранён на диск → завершите: --dialog=" + response.dialogId() + " --finish");
        System.out.println("===================");

        maybeExit(args);
    }

    private static void printMemory(java.util.List<com.yunovan.aiadvent.agent.dialog.DialogMemory> memory) {
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
}