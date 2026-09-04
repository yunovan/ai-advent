package com.yunovan.aiadvent.day05;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day05CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day05CliRunner.class);

    private final Day05CompareService compareService;
    private final ApplicationContext applicationContext;

    public Day05CliRunner(Day05CompareService compareService, ApplicationContext applicationContext) {
        this.compareService = compareService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"5".equals(firstOption(args, "day"))) {
            log.info("Day 5 web UI: http://localhost:8080/day5.html  |  API: POST /api/day5/compare");
            return;
        }

        String prompt = firstOption(args, "prompt");
        if (prompt == null || prompt.isBlank()) {
            prompt = Day5Models.DEFAULT_PROMPT;
            log.info("Day 5 CLI: using default prompt");
        }

        log.info("Day 5 CLI: same prompt on weak / medium / strong models");
        ModelComparisonResponse result = compareService.compare(prompt);
        System.out.println();
        System.out.println("=== PROMPT ===");
        System.out.println(result.prompt());
        for (ModelRun run : result.runs()) {
            System.out.println();
            System.out.println("=== " + run.label() + " · " + run.model() + " ===");
            System.out.println(run.content());
            System.out.println(
                    run.elapsedMs()
                            + " мс, "
                            + Day5Models.formatTokens(run.totalTokens())
                            + ", "
                            + run.costLabel()
                            + ", "
                            + run.words()
                            + " слов");
            System.out.println("OpenRouter: " + run.openRouterUrl());
            System.out.println("Hugging Face: " + run.huggingFaceUrl());
        }
        System.out.println();
        System.out.println("=== CONCLUSION ===");
        System.out.println(result.conclusion());
        System.out.println();
        System.out.println("=== LINKS ===");
        for (ModelLink link : result.links()) {
            System.out.println(link.title() + ": " + link.url());
        }
        System.out.println("====================");

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
