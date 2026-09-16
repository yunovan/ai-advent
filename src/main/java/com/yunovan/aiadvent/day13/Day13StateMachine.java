package com.yunovan.aiadvent.day13;

import java.util.Map;
import java.util.Set;

/**
 * Конечный автомат состояния задачи: planning → execution → validation → done.
 */
public final class Day13StateMachine {

    private static final Map<Day13Stage, Day13Stage> NEXT = Map.of(
            Day13Stage.PLANNING, Day13Stage.EXECUTION,
            Day13Stage.EXECUTION, Day13Stage.VALIDATION,
            Day13Stage.VALIDATION, Day13Stage.DONE);

    private static final Map<Day13Stage, Set<Day13Stage>> TRANSITIONS = Map.of(
            Day13Stage.PLANNING, Set.of(Day13Stage.EXECUTION),
            Day13Stage.EXECUTION, Set.of(Day13Stage.VALIDATION),
            Day13Stage.VALIDATION, Set.of(Day13Stage.DONE),
            Day13Stage.DONE, Set.of());

    private Day13StateMachine() {
    }

    public static Day13Stage next(Day13Stage stage) {
        Day13Stage next = NEXT.get(stage);
        if (next == null) {
            throw new IllegalStateException("Этап '" + stage.display() + "' — терминальный, переход невозможен");
        }
        return next;
    }

    public static boolean canTransition(Day13Stage from, Day13Stage to) {
        return TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static boolean isTerminal(Day13Stage stage) {
        return stage == Day13Stage.DONE;
    }

    public static String defaultExpectedAction(Day13Stage stage) {
        return switch (stage) {
            case PLANNING -> "Составить план задачи и разбить её на шаги";
            case EXECUTION -> "Выполнить следующий шаг по плану";
            case VALIDATION -> "Проверить результат по критериям готовности";
            case DONE -> "Задача завершена";
        };
    }
}