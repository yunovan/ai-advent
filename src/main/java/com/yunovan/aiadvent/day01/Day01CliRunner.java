package com.yunovan.aiadvent.day01;

import com.yunovan.aiadvent.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day01CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day01CliRunner.class);

    private final LlmClient llmClient;
    private final ApplicationContext applicationContext;

    public Day01CliRunner(LlmClient llmClient, ApplicationContext applicationContext) {
        this.llmClient = llmClient;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if ("2".equals(firstOption(args, "day"))
                || "3".equals(firstOption(args, "day"))
                || "4".equals(firstOption(args, "day"))) {
            return;
        }

        String prompt = firstOption(args, "prompt");
        if (prompt == null || prompt.isBlank()) {
            log.info("Day 1 web UI: http://localhost:8080  |  API: POST /api/day1/chat");
            return;
        }

        log.info("Day 1 CLI: sending prompt to LLM");
        String content = llmClient.complete(prompt);
        System.out.println();
        System.out.println("=== LLM response ===");
        System.out.println(content);
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
