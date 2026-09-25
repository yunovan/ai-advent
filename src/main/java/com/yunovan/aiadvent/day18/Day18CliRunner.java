package com.yunovan.aiadvent.day18;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
@EnableConfigurationProperties(Day18Properties.class)
public class Day18CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day18CliRunner.class);

    private final Day18SchedulerApi scheduler;
    private final Day18AgentService service;
    private final Day18Properties properties;
    private final ApplicationContext applicationContext;

    public Day18CliRunner(Day18SchedulerApi scheduler, Day18AgentService service,
                          Day18Properties properties, ApplicationContext applicationContext) {
        this.scheduler = scheduler;
        this.service = service;
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"18".equals(firstOption(args, "day"))) {
            log.info("Day 18 web UI: http://localhost:8080/day18.html  |  API: /api/day18/health, "
                    + "/api/day18/tools, /api/day18/agent, /api/day18/jobs, /api/day18/summary  |  "
                    + "MCP: http://localhost:{}{}", properties.serverPort(), properties.path());
            return;
        }

        try {
            if (args.containsOption("check")) {
                Day18HealthResponse health = service.health();
                System.out.println("=== СОЕДИНЕНИЕ С MCP ===");
                System.out.println("connected: " + health.connected());
                System.out.println("server: " + health.serverName() + " " + health.serverVersion());
                System.out.println("tools: " + health.toolCount());
                maybeExit(args);
                return;
            }

            if (args.containsOption("tools")) {
                System.out.println("=== ИНСТРУМЕНТЫ MCP ===");
                for (Day18ToolInfo tool : service.tools()) {
                    System.out.println("- " + tool.name() + ": " + tool.description());
                }
                maybeExit(args);
                return;
            }

            if (args.containsOption("jobs")) {
                printJobs(scheduler.listJobs());
                maybeExit(args);
                return;
            }

            String reminder = firstOption(args, "reminder");
            if (reminder != null) {
                Day18Job job = scheduler.addReminder(reminder, optionInt(args, "delay", 10));
                System.out.println("=== НАПОМИНАНИЕ ===");
                printJob(job);
                maybeExit(args);
                return;
            }

            if (args.containsOption("collect")) {
                Day18Job job = scheduler.addCollector(
                        firstOption(args, "feed"),
                        optionInt(args, "period", 5),
                        firstOption(args, "url"),
                        firstOption(args, "source"));
                System.out.println("=== ПЕРИОДИЧЕСКИЙ СБОР ===");
                printJob(job);
                maybeExit(args);
                return;
            }

            String runJobId = firstOption(args, "run");
            if (runJobId != null) {
                Day18Job job = scheduler.runNow(runJobId);
                System.out.println("=== ВЫПОЛНЕНО СЕЙЧАС ===");
                printJob(job);
                maybeExit(args);
                return;
            }

            if (args.containsOption("summary")) {
                Day18Summary summary = scheduler.summary(
                        firstOption(args, "feed"), optionInt(args, "since", 0));
                System.out.println("=== СВОДКА ===");
                printSummary(summary);
                maybeExit(args);
                return;
            }

            if (args.containsOption("live")) {
                live(optionInt(args, "seconds", 20));
                maybeExit(args);
                return;
            }

            String prompt = firstOption(args, "prompt");
            if (prompt != null && !prompt.isBlank()) {
                Day18AgentResponse response = service.submit(prompt);
                System.out.println("=== АГЕНТ / MCP ===");
                System.out.println("prompt: " + response.prompt());
                System.out.println("tool: " + (response.tool() == null ? "—" : response.tool()));
                System.out.println("arguments: " + (response.arguments() == null ? "—" : response.arguments()));
                System.out.println("tool result: " + (response.toolResult() == null ? "—" : response.toolResult()));
                System.out.println("answer: ");
                System.out.println(response.answer());
                maybeExit(args);
                return;
            }
        } catch (Day18McpException ex) {
            System.out.println("ОШИБКА: " + ex.getMessage());
            maybeExit(args);
            return;
        }

        System.out.println("Планировщик 24/7 доступен на http://localhost:8080/day18.html и /api/day18/agent");
        System.out.println("Проверка: --check | Инструменты: --tools | Задания: --jobs | "
                + "Запрос: --prompt=<текст>");
        System.out.println("Напоминание: --reminder=<текст> --delay=<сек>");
        System.out.println("Сбор: --collect --feed=<имя> --period=<сек> [--url=...] [--source=<поток>]");
        System.out.println("Выполнить: --run=<id> | Сводка: --summary [--feed=<имя>] | "
                + "Наблюдение: --live [--seconds=<сек>]");
    }

    private void live(int seconds) {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        System.out.println("=== ПЛАНИРОВЩИК 24/7 (наблюдение) ===");
        int iteration = 0;
        while (System.currentTimeMillis() < deadline) {
            iteration++;
            System.out.println("--- тик " + iteration + " (" + java.time.LocalTime.now()
                    .withNano(0) + ") ---");
            printJobs(scheduler.listJobs());
            Day18Summary summary = scheduler.summary(null, 0);
            if (summary.count() > 0) {
                printSummary(summary);
            } else {
                System.out.println("событий пока нет");
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static void printJobs(List<Day18Job> jobs) {
        System.out.println("заданий: " + jobs.size());
        for (Day18Job job : jobs) {
            System.out.println("- " + job.getId() + " [" + job.getType() + "] «" + job.getName()
                    + "» статус " + job.getStatus()
                    + ", запусков " + job.getRunCount()
                    + ", следующий запуск " + (job.getNextRunAt() == null ? "—" : job.getNextRunAt())
                    + (job.getLastResult() == null ? "" : ", итог: " + job.getLastResult()));
        }
    }

    private static void printJob(Day18Job job) {
        System.out.println("id: " + job.getId());
        System.out.println("type: " + job.getType());
        System.out.println("name: " + job.getName());
        System.out.println("feed: " + job.getFeed());
        System.out.println("status: " + job.getStatus());
        System.out.println("periodSeconds: " + job.getPeriodSeconds());
        System.out.println("delaySeconds: " + job.getDelaySeconds());
        System.out.println("nextRunAt: " + job.getNextRunAt());
        System.out.println("runCount: " + job.getRunCount());
        System.out.println("lastResult: " + job.getLastResult());
    }

    private static void printSummary(Day18Summary summary) {
        System.out.println("feed: " + (summary.feed() == null ? "все потоки" : summary.feed()));
        System.out.println("событий: " + summary.count());
        if (summary.count() > 0) {
            System.out.println("первое: " + summary.firstAt());
            System.out.println("последнее: " + summary.lastAt());
            System.out.println("среднее значение: " + summary.avgValue());
            System.out.println("мин/макс: " + summary.minValue() + " / " + summary.maxValue());
            if (summary.successRate() != null) {
                System.out.println("доля успешных проверок: " + Math.round(summary.successRate() * 100) + "%");
            }
            System.out.println("последняя запись: " + summary.lastPayload());
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

    private static int optionInt(ApplicationArguments args, String name, int fallback) {
        String value = firstOption(args, name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}