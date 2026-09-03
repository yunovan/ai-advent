package com.yunovan.aiadvent.day04;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day04CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day04CliRunner.class);

    private final Day04TemperatureService temperatureService;
    private final ApplicationContext applicationContext;

    public Day04CliRunner(Day04TemperatureService temperatureService, ApplicationContext applicationContext) {
        this.temperatureService = temperatureService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"4".equals(firstOption(args, "day"))) {
            log.info("Day 4 web UI: http://localhost:8080/day4.html  |  API: POST /api/day4/compare");
            return;
        }

        String prompt = firstOption(args, "prompt");
        if (prompt == null || prompt.isBlank()) {
            prompt = Day04Temperatures.DEFAULT_PROMPT;
            log.info("Day 4 CLI: using default prompt");
        }

        log.info("Day 4 CLI: same prompt at temperature 0 / 0.7 / 1.2");
        TemperatureResponse result = temperatureService.compare(prompt);
        System.out.println();
        System.out.println("=== PROMPT ===");
        System.out.println(result.prompt());
        for (TemperatureSample sample : result.samples()) {
            System.out.println();
            System.out.println("=== temperature=" + sample.temperature() + " ===");
            System.out.println(sample.content());
            System.out.println("(" + sample.words() + " words, " + sample.bestFor() + ")");
        }
        System.out.println();
        System.out.println("=== CONCLUSIONS ===");
        System.out.println(result.conclusions());
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
