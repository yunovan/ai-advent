package com.yunovan.aiadvent.day13;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day13CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day13CliRunner.class);

    private final Day13TaskService service;
    private final ApplicationContext applicationContext;

    public Day13CliRunner(Day13TaskService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"13".equals(firstOption(args, "day"))) {
            log.info("Day 13 web UI: http://localhost:8080/day13.html  |  API: POST /api/day13/tasks");
            return;
        }

        String create = firstOption(args, "create");
        if (create != null && !create.isBlank()) {
            Day13Task task = service.create(create);
            System.out.println("Создана задача: " + task.id() + " — " + task.title());
            System.out.println("- Этап: " + task.stage().display()
                    + " | Шаг: " + task.step()
                    + " | Ожидаемое действие: " + task.expectedAction());
            maybeExit(args);
            return;
        }

        if (args.containsOption("list")) {
            System.out.println("=== ЗАДАЧИ ДНЯ 13 (state machine) ===");
            for (Day13Task task : service.list()) {
                printTask(task);
            }
            maybeExit(args);
            return;
        }

        String taskId = firstOption(args, "task");
        if (taskId == null || taskId.isBlank()) {
            log.info("Day 13 CLI: --create=\"Задача\" | --list | "
                    + "--task=<id> [--state | --advance | --step=N | --expected-action=\"...\" | "
                    + "--note=\"...\" | --pause | --resume | --prompt=\"...\" [--limit=N]]");
            return;
        }

        if (args.containsOption("advance")) {
            Day13TaskState state = service.advance(taskId);
            printState(state);
            maybeExit(args);
            return;
        }

        if (args.containsOption("pause")) {
            printState(service.pause(taskId));
            maybeExit(args);
            return;
        }

        if (args.containsOption("resume")) {
            printState(service.resume(taskId));
            maybeExit(args);
            return;
        }

        String stepValue = firstOption(args, "step");
        if (stepValue != null && !stepValue.isBlank()) {
            printState(service.setStep(taskId, positiveInt(stepValue)));
            maybeExit(args);
            return;
        }

        String expectedAction = firstOption(args, "expected-action");
        if (expectedAction != null && !expectedAction.isBlank()) {
            printState(service.setExpectedAction(taskId, expectedAction));
            maybeExit(args);
            return;
        }

        String note = firstOption(args, "note");
        if (note != null && !note.isBlank()) {
            printState(service.addNote(taskId, note));
            maybeExit(args);
            return;
        }

        String request = firstOption(args, "prompt");
        if (request == null || request.isBlank()) {
            Day13Task task = service.get(taskId);
            if (task == null) {
                System.out.println("Задача '" + taskId + "' не найдена");
            } else {
                printTask(task);
            }
            maybeExit(args);
            return;
        }

        Long limit = positiveLong(firstOption(args, "limit"));
        Day13ContinueResponse response = service.continueTask(taskId, request, limit);
        System.out.println("=== ЗАДАЧА '" + response.taskId() + "' ===");
        System.out.println("Этап: " + response.state().stage().display()
                + " | Шаг: " + response.state().step()
                + " | Ожидаемое действие: " + response.state().expectedAction()
                + (response.state().paused() ? " | ПАУЗА" : ""));
        System.out.println();
        System.out.println("=== AGENT REPLY ===");
        System.out.println(response.content());
        System.out.println("model: " + response.model() + " · " + response.elapsedMs() + " мс"
                + " · промпт ~" + response.promptTokens());
        if (response.exceeded()) {
            System.out.println("=== ПРЕВЫШЕН ЛИМИТ ===");
        }
        maybeExit(args);
    }

    private void printTask(Day13Task task) {
        System.out.println(task.id() + " — " + task.title());
        System.out.println("  Этап: " + task.stage().display()
                + " | Шаг: " + task.step()
                + (task.paused() ? " | ПАУЗА" : "")
                + (task.isFinished() ? " | ГОТОВО" : ""));
        System.out.println("  Ожидаемое действие: " + task.expectedAction());
        if (!task.notes().isEmpty()) {
            System.out.println("  Заметки:");
            for (String item : task.notes()) {
                System.out.println("    - " + item);
            }
        }
    }

    private void printState(Day13TaskState state) {
        System.out.println("=== ЗАДАЧА '" + state.taskId() + "' ===");
        System.out.println("Название: " + state.title());
        System.out.println("Этап: " + state.stage().display());
        System.out.println("Шаг: " + state.step());
        System.out.println("Ожидаемое действие: " + state.expectedAction());
        System.out.println("Пауза: " + (state.paused() ? "да" : "нет"));
        System.out.println("Завершена: " + (state.finishedAt() != null ? "да" : "нет"));
        if (!state.notes().isEmpty()) {
            System.out.println("Заметки:");
            for (String item : state.notes()) {
                System.out.println("  - " + item);
            }
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

    private static int positiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : 0;
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}