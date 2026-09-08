package com.yunovan.aiadvent.day07;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.ConversationReply;
import com.yunovan.aiadvent.agent.ConversationalAgent;
import com.yunovan.aiadvent.agent.ContextualChatAgent;
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

    private final ConversationalAgent agent;
    private final ApplicationContext applicationContext;

    public Day07CliRunner(ConversationalAgent agent, ApplicationContext applicationContext) {
        this.agent = agent;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"7".equals(firstOption(args, "day"))) {
            log.info("Day 7 web UI: http://localhost:8080/day7.html  |  API: POST /api/day7/chat");
            return;
        }

        String sessionId = firstOption(args, "session");
        if (sessionId != null && !sessionId.isBlank()) {
            sessionId = ContextualChatAgent.normalize(sessionId);
        } else {
            sessionId = ContextualChatAgent.DEFAULT_SESSION_ID;
        }

        if (args.containsOption("reset")) {
            agent.reset(sessionId);
            System.out.println("Диалог '" + sessionId + "' сброшен: история удалена.");
            System.out.println("=== CLI: перезапустите с --prompt, чтобы начать заново ===");
            maybeExit(args);
            return;
        }

        String request = firstOption(args, "prompt");
        if (request == null || request.isBlank()) {
            log.info("Day 7 CLI: prompt is required (--prompt=\"...\") / --reset to clear history");
            return;
        }

        log.info("Day 7 CLI: continuing conversation '" + sessionId + "'");
        ConversationReply reply = agent.ask(sessionId, request);
        System.out.println();
        System.out.println("=== CONVERSATION '" + reply.sessionId() + "' (" + reply.messageCount() + " сообщений) ===");
        for (ConversationMessage message : reply.messages()) {
            System.out.println();
            System.out.println("[" + message.role() + "]");
            System.out.println(message.content());
        }
        System.out.println();
        System.out.println("=== AGENT REPLY ===");
        System.out.println(reply.content());
        System.out.println("model: " + reply.model() + " · " + reply.elapsedMs() + " мс · история сохранена на диск");
        System.out.println("===================");

        maybeExit(args);
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