package com.yunovan.aiadvent.day26;

public record Day26TaskResult(
        String id,
        String title,
        String complexity,
        String prompt,
        String status,
        String reply,
        String error,
        String model,
        long latencyMs,
        int promptTokens,
        int outputTokens,
        double tokensPerSecond) {

    public static final String STATUS_OK = "ok";
    public static final String STATUS_FAILED = "failed";

    public static Day26TaskResult ok(Day26Task task, Day26Answer answer) {
        return new Day26TaskResult(
                task.id(), task.title(), task.complexity(), task.prompt(),
                STATUS_OK, answer.reply(), "", answer.model(),
                answer.latencyMs(), answer.promptTokens(), answer.outputTokens(),
                answer.tokensPerSecond());
    }

    public static Day26TaskResult failed(Day26Task task, String error) {
        return new Day26TaskResult(
                task.id(), task.title(), task.complexity(), task.prompt(),
                STATUS_FAILED, "", error == null ? "неизвестная ошибка" : error,
                "", 0, 0, 0, 0.0);
    }

    public boolean ok() {
        return STATUS_OK.equals(status);
    }
}
