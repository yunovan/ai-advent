package com.yunovan.aiadvent.day03;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day03CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day03CliRunner.class);

    private final Day03ReasoningService reasoningService;
    private final ApplicationContext applicationContext;

    public Day03CliRunner(Day03ReasoningService reasoningService, ApplicationContext applicationContext) {
        this.reasoningService = reasoningService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"3".equals(firstOption(args, "day"))) {
            log.info("Day 3 web UI: http://localhost:8080/day3.html  |  API: POST /api/day3/compare");
            return;
        }

        String prompt = firstOption(args, "prompt");
        if (prompt == null || prompt.isBlank()) {
            prompt = Day03Prompts.DEFAULT_TASK;
            log.info("Day 3 CLI: using default task");
        }

        log.info("Day 3 CLI: four reasoning methods for one task");
        ReasoningResponse result = reasoningService.solve(prompt);
        System.out.println();
        System.out.println("=== TASK ===");
        System.out.println(result.task());
        printMethod("1. DIRECT", result.direct());
        printMethod("2. STEP BY STEP", result.stepByStep());
        System.out.println();
        System.out.println("=== 3. META-PROMPT (generated) ===");
        System.out.println(result.metaPrompt().generatedPrompt());
        printMethod("3. META-PROMPT (solution)", result.metaPrompt());
        printMethod("4. EXPERTS", result.experts());
        System.out.println("====================");

        if (args.containsOption("cli")) {
            int code = SpringApplication.exit(applicationContext, () -> 0);
            System.exit(code);
        }
    }

    private static void printMethod(String heading, MethodResult method) {
        System.out.println();
        System.out.println("=== " + heading + " ===");
        System.out.println(method.content());
        System.out.println("(finish_reason=" + method.finishReason() + ")");
    }

    private static String firstOption(ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }
}
