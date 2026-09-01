package com.yunovan.aiadvent.day02;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day02CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day02CliRunner.class);

    private final Day02CompareService compareService;
    private final ApplicationContext applicationContext;

    public Day02CliRunner(Day02CompareService compareService, ApplicationContext applicationContext) {
        this.compareService = compareService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"2".equals(firstOption(args, "day"))) {
            log.info("Day 2 web UI: http://localhost:8080/day2.html  |  API: POST /api/day2/compare");
            return;
        }

        String prompt = firstOption(args, "prompt");
        if (prompt == null || prompt.isBlank()) {
            log.warn("Day 2 CLI needs --prompt=... together with --day=2");
            return;
        }

        log.info("Day 2 CLI: same prompt, unconstrained vs constrained");
        CompareResponse result = compareService.compare(prompt);
        System.out.println();
        System.out.println("=== SAME PROMPT ===");
        System.out.println(result.prompt());
        System.out.println();
        System.out.println("=== WITHOUT CONSTRAINTS ===");
        System.out.println(result.unconstrained().content());
        System.out.println("(" + result.unconstrained().words() + " words, finish_reason="
                + result.unconstrained().finishReason() + ")");
        System.out.println();
        System.out.println("=== WITH CONSTRAINTS ===");
        System.out.println("format + max_tokens=" + result.constrained().maxTokens()
                + " + stop=" + result.constrained().stop());
        System.out.println(result.constrained().content());
        System.out.println("(" + result.constrained().words() + " words, finish_reason="
                + result.constrained().finishReason() + ")");
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
