package com.yunovan.aiadvent.day15;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Day15CliRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Day15CliRunner.class);

    private final Day15TaskService service;
    private final ApplicationContext applicationContext;

    public Day15CliRunner(Day15TaskService service, ApplicationContext applicationContext) {
        this.service = service;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"15".equals(firstOption(args, "day"))) {
            log.info("Day 15 web UI: http://localhost:8080/day15.html  |  API: POST /api/day15/tasks");
            return;
        }

        String create = firstOption(args, "create");
        if (create != null && !create.isBlank()) {
            Day15Task task = service.create(create);
            System.out.println("Создана задача: " + task.id() + " — " + task.title());
            printState(task.state());
            maybeExit(args);
            return;
        }

        if (args.containsOption("list")) {
            System.out.println("=== ЗАДАЧИ ДНЯ 15 (контролируемые переходы) ===");
            for (Day15Task task : service.list()) {
                System.out.println(task.id() + " — " + task.title()
                        + " | состояние: " + task.stage().display()
                        + (task.paused() ? " | ПАУЗА" : "")
                        + (task.isFinished() ? " | ГОТОВО" : ""));
            }
            maybeExit(args);
            return;
        }

        String taskId = firstOption(args, "task");
        if (taskId == null || taskId.isBlank()) {
            log.info("Day 15 CLI: --create=\"Задача\" | --list | "
                    + "--task=<id> [--state | --advance | --transition=проверка | --step=N | "
                    + "--expected-action=\"...\" | --note=\"...\" | --pause | --resume | --prompt=\"...\" [--limit=N]]");
            return;
        }

        if (args.containsOption("transition")) {
            String target = firstOption(args, "transition");
            printState(service.transition(taskId, target));
            maybeExit(args);
            return;
        }

        if (args.containsOption("advance")) {
            printState(service.advance(taskId));
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
            Day15Task task = service.get(taskId);
            if (task == null) {
                System.out.println("Задача '" + taskId + "' не найдена");
            } else {
                printState(task.state());
            }
            maybeExit(args);
            return;
        }

        Long limit = positiveLong(firstOption(args, "limit"));
        Day15ContinueResponse response = service.continueTask(taskId, request, limit);
        System.out.println("=== ЗАДАЧА '" + response.taskId() + "' ===");
        System.out.println("Состояние: " + response.state().stage().display()
                + " | Шаг: " + response.state().step()
                + " | Разрешено: " + response.state().allowedTransitions().stream()
                        .map(Day15Stage::display).toList()
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

    private void printState(Day15TaskState state) {
        System.out.println("=== ЗАДАЧА '" + state.taskId() + "' ===");
        System.out.println("Название: " + state.title());
        System.out.println("Состояние: " + state.stage().display());
        System.out.println("Разрешённые переходы: "
                + state.allowedTransitions().stream().map(Day15Stage::display).toList());
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