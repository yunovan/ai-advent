package com.yunovan.aiadvent.day15;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record Day15Task(
        String id,
        String title,
        Day15Stage stage,
        int step,
        String expectedAction,
        boolean paused,
        List<String> notes,
        List<Day15TaskMessage> history,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt) {

    public static Day15Task create(String title) {
        Instant now = Instant.now();
        return new Day15Task(
                UUID.randomUUID().toString(),
                title,
                Day15Stage.PLANNING,
                1,
                Day15StateMachine.defaultAction(Day15Stage.PLANNING),
                false,
                List.of(),
                List.of(),
                now,
                now,
                null);
    }

    public Day15Task withStage(Day15Stage value) {
        return new Day15Task(id, title, value, step, Day15StateMachine.defaultAction(value),
                paused, notes, history, createdAt, now(), finishedAt);
    }

    public Day15Task withStep(int value) {
        return new Day15Task(id, title, stage, value, expectedAction, paused, notes, history, createdAt, now(), finishedAt);
    }

    public Day15Task withExpectedAction(String value) {
        return new Day15Task(id, title, stage, step, value, paused, notes, history, createdAt, now(), finishedAt);
    }

    public Day15Task withPaused(boolean value) {
        return new Day15Task(id, title, stage, step, expectedAction, value, notes, history, createdAt, now(), finishedAt);
    }

    public Day15Task withNote(String note) {
        return new Day15Task(id, title, stage, step, expectedAction, paused, concatList(notes, note),
                history, createdAt, now(), finishedAt);
    }

    public Day15Task withMessage(Day15TaskMessage message) {
        return new Day15Task(id, title, stage, step, expectedAction, paused, notes,
                concatList(history, message), createdAt, now(), finishedAt);
    }

    public Day15Task withFinishedAt(Instant value) {
        return new Day15Task(id, title, stage, step, expectedAction, paused, notes, history, createdAt, now(), value);
    }

    public boolean isFinished() {
        return Day15StateMachine.isTerminal(stage);
    }

    public Day15TaskState state() {
        return new Day15TaskState(id, title, stage, Day15StateMachine.allowedTargets(stage),
                step, expectedAction, paused, notes, createdAt, updatedAt, finishedAt);
    }

    private static Instant now() {
        return Instant.now();
    }

    private static <T> List<T> concatList(List<T> base, T item) {
        List<T> copy = new ArrayList<>(base);
        copy.add(item);
        return List.copyOf(copy);
    }
}