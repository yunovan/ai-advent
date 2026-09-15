package com.yunovan.aiadvent.day12;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.day11.Day11MemoryEntry;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day12CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day12CliRunner.class);

    private final Day12DialogService service;
    private final ApplicationContext applicationContext;

    public Day12CliRunner(Day12DialogService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"12".equals(firstOption(args, "day"))) {
            log.info("Day 12 web UI: http://localhost:8080/day12.html  |  API: POST /api/day12/dialogs");
            return;
        }

        if (args.containsOption("profiles")) {
            System.out.println("=== ПРОФИЛИ пользователя (учитываются в каждом запросе) ===");
            for (Day12Profile profile : service.profiles()) {
                System.out.println(profile.id() + " — " + profile.display());
            }
            maybeExit(args);
            return;
        }

        String createProfile = firstOption(args, "create-profile");
        if (createProfile != null && !createProfile.isBlank()) {
            Day12Profile profile = parseAndCreate(createProfile);
            System.out.println("Создан профиль: " + profile.id() + " — " + profile.display());
            maybeExit(args);
            return;
        }

        String profileId = firstOption(args, "profile");
        if (args.containsOption("start")) {
            Day12StartResponse started = service.start(profileId);
            System.out.println("Создан диалог: " + started.dialogId());
            System.out.println("Профиль: " + started.profile().id() + " — " + started.profile().display());
            System.out.println("Доступные профили: " + started.profiles().size());
            maybeExit(args);
            return;
        }

        if (args.containsOption("list")) {
            System.out.println("=== Диалоги дня 12 (профиль + память) ===");
            for (Day12DialogInfo dialog : service.dialogs()) {
                String profile = dialog.profile() == null ? "—" : dialog.profile().name();
                System.out.println(dialog.dialogId()
                        + "  профиль: " + profile
                        + "  сообщений: " + dialog.messageCount()
                        + "  рабочая: " + dialog.working().size()
                        + "  долговременная: " + dialog.longTerm().size()
                        + (dialog.finishedAt() == null ? "" : "  завершён"));
            }
            maybeExit(args);
            return;
        }

        String dialogId = firstOption(args, "dialog");
        if (dialogId == null || dialogId.isBlank()) {
            log.info("Day 12 CLI: --profiles | --create-profile=\"Имя:Ася;Стиль:...;Формат:...;Ограничения:...;Заметки:...\" | "
                    + "--start [--profile=<id|имя>] | --list | "
                    + "--dialog=<id> --profile=<id|имя> | --prompt=\"...\" [--limit=N] | "
                    + "--remember=\"ключ: значение\" --layer=working | --finish");
            return;
        }

        if (args.containsOption("finish")) {
            Day12FinishResponse finished = service.finish(dialogId);
            System.out.println("=== ДИАЛОГ ЗАВЕРШЁН '" + finished.dialogId() + "' ===");
            System.out.println("Профиль: " + finished.profileId());
            System.out.println("Итог для долговременной памяти: " + finished.summary());
            System.out.println("Записей в долговременной памяти: " + finished.longTermEntryCount());
            maybeExit(args);
            return;
        }

        if (profileId != null && !profileId.isBlank()) {
            Day12DialogInfo switched = service.setProfile(dialogId, profileId);
            System.out.println("=== ДИАЛОГ '" + switched.dialogId() + "' переключён на профиль ===");
            System.out.println("- " + switched.profile().display());
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
            Day12DialogInfo info = service.remember(
                    dialogId,
                    remember.substring(0, separator).trim(),
                    remember.substring(separator + 1).trim(),
                    layer);
            printInfo(info);
            maybeExit(args);
            return;
        }

        String request = firstOption(args, "prompt");
        if (request == null || request.isBlank()) {
            Day12DialogInfo info = service.get(dialogId);
            printInfo(info);
            maybeExit(args);
            return;
        }

        Long limit = positiveLong(firstOption(args, "limit"));
        Day12ChatResponse response = service.chat(dialogId, request, limit);
        printMetrics(response);
        if (response.exceeded()) {
            System.out.println("=== ПРЕВЫШЕН ЛИМИТ ===");
            System.out.println(response.content());
        } else {
            System.out.println();
            System.out.println("=== DIALOG '" + response.dialogId() + "' (" + response.messageCount()
                    + " сообщений) · профиль: " + response.profile().name() + " ===");
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

    private Day12Profile parseAndCreate(String spec) {
        String name = "Новый пользователь";
        String style = null;
        String format = null;
        StringBuilder restrictions = new StringBuilder();
        String notes = null;
        for (String part : spec.split(";")) {
            int separator = part.indexOf(':');
            if (separator <= 0) {
                continue;
            }
            String key = part.substring(0, separator).trim().toLowerCase();
            String value = part.substring(separator + 1).trim();
            switch (key) {
                case "имя", "name" -> name = value;
                case "стиль", "style" -> style = value;
                case "формат", "format" -> format = value;
                case "ограничения", "restrictions" -> restrictions
                        .append(restrictions.isEmpty() ? "" : "|").append(value);
                case "заметки", "notes" -> notes = value;
                default -> { }
            }
        }
        List<String> restrictionsList = restrictions.isEmpty()
                ? Day12Personalizer.defaultRestrictions(name)
                : List.of(restrictions.toString().split("\\|"));
        return service.createProfile(name, style, format, restrictionsList, notes);
    }

    private void printMetrics(Day12ChatResponse response) {
        System.out.println();
        System.out.println("=== METRICS ===");
        System.out.println("context tokens:     " + response.contextTokens());
        System.out.println("request tokens:     " + response.requestTokens());
        System.out.println("profile tokens:     " + response.profileTokens());
        System.out.println("working tokens:     " + response.workingTokens());
        System.out.println("long-term tokens:   " + response.longTermTokens());
        System.out.println("response tokens:    " + response.responseTokens());
        System.out.println("prompt tokens:      " + response.promptTokens());
        System.out.println("context limit:      " + response.contextLimit()
                + (response.exceeded() ? " · превышен!" : ""));
        System.out.println("working entries:    " + response.working().size());
        System.out.println("long-term entries:  " + response.longTerm().size());
    }

    private void printInfo(Day12DialogInfo info) {
        System.out.println("=== ДИАЛОГ '" + info.dialogId() + "' (сообщений: " + info.messageCount()
                + (info.finishedAt() == null ? "" : ", завершён") + ") ===");
        System.out.println("Профиль: " + (info.profile() == null ? "—" : info.profile().display()));
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
            System.out.println("  " + entry.display());
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