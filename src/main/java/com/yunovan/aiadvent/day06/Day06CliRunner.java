package com.yunovan.aiadvent.day06;

import com.yunovan.aiadvent.agent.Agent;
import com.yunovan.aiadvent.agent.AgentReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day06CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day06CliRunner.class);

    private final Agent agent;
    private final ApplicationContext applicationContext;

    public Day06CliRunner(Agent agent, ApplicationContext applicationContext) {
        this.agent = agent;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"6".equals(firstOption(args, "day"))) {
            log.info("Day 6 web UI: http://localhost:8080/day6.html  |  API: POST /api/day6/chat");
            return;
        }

        String request = firstOption(args, "prompt");
        if (request == null || request.isBlank()) {
            log.info("Day 6 CLI: prompt is required (--prompt=\"...\")");
            return;
        }

        log.info("Day 6 CLI: agent accepts the request and calls the LLM");
        AgentReply reply = agent.ask(request);
        System.out.println();
        System.out.println("=== AGENT REPLY ===");
        System.out.println(reply.content());
        System.out.println("model: " + reply.model() + " · " + reply.elapsedMs() + " мс");
        System.out.println("===================");

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