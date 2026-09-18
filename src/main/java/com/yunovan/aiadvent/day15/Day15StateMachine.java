package com.yunovan.aiadvent.day15;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Контролируемый жизненный цикл задачи: явно заданные состояния и разрешённые
 * направленные переходы. Нельзя «перепрыгнуть» этап: реализация невозможна до
 * утверждённого плана, завершение — только после проверки.
 */
public final class Day15StateMachine {

    public static final String CHAIN =
            "планирование → план утверждён → выполнение → проверка → готово";

    private static final Map<Day15Stage, Set<Day15Stage>> ALLOWED = Map.of(
            Day15Stage.PLANNING, Set.of(Day15Stage.PLAN_APPROVED),
            Day15Stage.PLAN_APPROVED, Set.of(Day15Stage.EXECUTION, Day15Stage.PLANNING),
            Day15Stage.EXECUTION, Set.of(Day15Stage.VALIDATION),
            Day15Stage.VALIDATION, Set.of(Day15Stage.DONE, Day15Stage.EXECUTION),
            Day15Stage.DONE, Set.of());

    private Day15StateMachine() {
    }

    public static boolean canTransition(Day15Stage from, Day15Stage to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static Day15Stage transition(Day15Stage from, Day15Stage to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(illegalTransitionMessage(from, to));
        }
        return to;
    }

    public static List<Day15Stage> allowedTargets(Day15Stage stage) {
        return ALLOWED.getOrDefault(stage, Set.of()).stream()
                .sorted()
                .toList();
    }

    public static Day15Stage nextForward(Day15Stage stage) {
        return switch (stage) {
            case PLANNING -> Day15Stage.PLAN_APPROVED;
            case PLAN_APPROVED -> Day15Stage.EXECUTION;
            case EXECUTION -> Day15Stage.VALIDATION;
            case VALIDATION -> Day15Stage.DONE;
            case DONE -> throw new IllegalStateException(
                    "Задача завершена: '" + stage.display() + "' — терминальное состояние, переход невозможен");
        };
    }

    public static boolean isTerminal(Day15Stage stage) {
        return stage == Day15Stage.DONE;
    }

    public static String defaultAction(Day15Stage stage) {
        return switch (stage) {
            case PLANNING -> "Составить план работ и добиться его утверждения";
            case PLAN_APPROVED -> "Начать реализацию по утверждённому плану";
            case EXECUTION -> "Выполнить следующий шаг по утверждённому плану";
            case VALIDATION -> "Проверить результат: завершить задачу или вернуть на доработку";
            case DONE -> "Задача завершена";
        };
    }

    public static String displayAllowed(Day15Stage stage) {
        List<Day15Stage> targets = allowedTargets(stage);
        if (targets.isEmpty()) {
            return "нет переходов (задача завершена)";
        }
        return targets.stream().map(Day15Stage::display).collect(Collectors.joining(", "));
    }

    public static String illegalTransitionMessage(Day15Stage from, Day15Stage to) {
        return "Недопустимый переход: '" + from.display() + "' → '" + to.display() + "'. "
                + "Жизненный цикл задачи: " + CHAIN + ". "
                + "Из состояния '" + from.display() + "' разрешено перейти только в: "
                + displayAllowed(from) + ". "
                + "Нельзя перепрыгивать этапы: реализация невозможна без утверждённого плана, "
                + "завершение — только после проверки.";
    }
}