package com.yunovan.aiadvent.day13;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record Day13Task(
        String id,
        String title,
        Day13Stage stage,
        int step,
        String expectedAction,
        boolean paused,
        List<String> notes,
        List<Day13TaskMessage> history,
        Instant createdAt,
        Instant updatedAt,
        Instant finishedAt) {

    public static Day13Task create(String title) {
        Instant now = Instant.now();
        return new Day13Task(
                UUID.randomUUID().toString(),
                title,
                Day13Stage.PLANNING,
                1,
                Day13StateMachine.defaultExpectedAction(Day13Stage.PLANNING),
                false,
                List.of(),
                List.of(),
                now,
                now,
                null);
    }

    public Day13Task withStage(Day13Stage value) {
        return new Day13Task(id, title, value, 1, Day13StateMachine.defaultExpectedAction(value),
                paused, notes, history, createdAt, now(), finishedAt);
    }

    public Day13Task withStep(int value) {
        return new Day13Task(id, title, stage, value, expectedAction, paused, notes, history, createdAt, now(), finishedAt);
    }

    public Day13Task withExpectedAction(String value) {
        return new Day13Task(id, title, stage, step, value, paused, notes, history, createdAt, now(), finishedAt);
    }

    public Day13Task withPaused(boolean value) {
        return new Day13Task(id, title, stage, step, expectedAction, value, notes, history, createdAt, now(), finishedAt);
    }

    public Day13Task withNote(String note) {
        return new Day13Task(id, title, stage, step, expectedAction, paused, concatList(notes, note),
                history, createdAt, now(), finishedAt);
    }

    public Day13Task withMessage(Day13TaskMessage message) {
        return new Day13Task(id, title, stage, step, expectedAction, paused, notes,
                concatList(history, message), createdAt, now(), finishedAt);
    }

    public Day13Task withFinishedAt(Instant value) {
        return new Day13Task(id, title, stage, step, expectedAction, paused, notes, history, createdAt, now(), value);
    }

    public boolean isFinished() {
        return Day13StateMachine.isTerminal(stage);
    }

    public Day13TaskState state() {
        return new Day13TaskState(id, title, stage, step, expectedAction, paused, notes, createdAt, updatedAt, finishedAt);
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