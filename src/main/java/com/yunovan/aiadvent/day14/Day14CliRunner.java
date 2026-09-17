package com.yunovan.aiadvent.day14;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day14CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day14CliRunner.class);

    private final Day14InvariantService service;
    private final ApplicationContext applicationContext;

    public Day14CliRunner(Day14InvariantService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"14".equals(firstOption(args, "day"))) {
            log.info("Day 14 web UI: http://localhost:8080/day14.html  |  API: POST /api/day14/advise");
            return;
        }

        String createCategory = firstOption(args, "category");
        String createTitle = firstOption(args, "title");
        String createDescription = firstOption(args, "description");
        if (createTitle != null && !createTitle.isBlank()) {
            Day14Invariant invariant = service.create(
                    createCategory, createTitle, createDescription == null ? createTitle : createDescription);
            System.out.println("Создан инвариант: " + invariant.id());
            printInvariant(invariant);
            maybeExit(args);
            return;
        }

        if (args.containsOption("invariants")) {
            System.out.println("=== ИНВАРИАНТЫ ДНЯ 14 ===");
            for (Day14Invariant invariant : service.list()) {
                printInvariant(invariant);
            }
            maybeExit(args);
            return;
        }

        String invariantId = firstOption(args, "invariant");
        if (invariantId != null && !invariantId.isBlank()) {
            if (args.containsOption("deactivate")) {
                printInvariant(service.deactivate(invariantId));
                maybeExit(args);
                return;
            }
            if (args.containsOption("delete")) {
                boolean deleted = service.delete(invariantId);
                System.out.println(deleted ? "Инвариант '" + invariantId + "' удалён" : "Инвариант не найден");
                maybeExit(args);
                return;
            }
            printInvariant(service.get(invariantId));
            maybeExit(args);
            return;
        }

        String request = firstOption(args, "prompt");
        if (request != null && !request.isBlank()) {
            Long limit = positiveLong(firstOption(args, "limit"));
            Day14AdviseResponse response = service.advise(request, limit);
            System.out.println("=== ЗАПРОС ===");
            System.out.println(response.request());
            System.out.println();
            System.out.println("=== AGENT REPLY ===");
            System.out.println(response.content());
            System.out.println("model: " + response.model() + " · " + response.elapsedMs() + " мс"
                    + " · промпт ~" + response.promptTokens());
            if (response.exceeded()) {
                System.out.println("=== ПРЕВЫШЕН ЛИМИТ ===");
            }
            maybeExit(args);
            return;
        }

        System.out.println(service.statusLine());
        log.info("Day 14 CLI: --category=стек --title=\"...\" --description=\"...\" | --invariants | "
                + "--invariant=<id> [--deactivate | --delete] | --prompt=\"...\" [--limit=N]");
    }

    private void printInvariant(Day14Invariant invariant) {
        System.out.println(invariant.category().display()
                + (invariant.active() ? "" : " [выключен]")
                + " — " + invariant.title());
        System.out.println("  " + invariant.description());
        System.out.println("  id: " + invariant.id());
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