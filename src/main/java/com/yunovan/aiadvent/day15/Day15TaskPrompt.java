package com.yunovan.aiadvent.day15;

import java.util.List;

public final class Day15TaskPrompt {

    private Day15TaskPrompt() {
    }

    public static String taskBlock(Day15Task task) {
        StringBuilder builder = new StringBuilder();
        builder.append("Состояние задачи (контролируемый жизненный цикл):\n");
        builder.append("- Задача: ").append(task.title()).append("\n");
        builder.append("- Состояние: ").append(task.stage().display())
                .append(task.stage() == Day15Stage.DONE ? "" : " (цепочка: " + Day15StateMachine.CHAIN + ")")
                .append("\n");
        builder.append("- Текущий шаг: ").append(task.step()).append("\n");
        builder.append("- Ожидаемое действие: ").append(task.expectedAction()).append("\n");
        builder.append("- Разрешённые переходы из '").append(task.stage().display()).append("': ")
                .append(Day15StateMachine.displayAllowed(task.stage())).append("\n");
        if (!task.notes().isEmpty()) {
            builder.append("Заметки по задаче:\n");
            for (String note : task.notes()) {
                builder.append("  - ").append(note).append("\n");
            }
        }
        if (!task.history().isEmpty()) {
            builder.append("История диалога:\n");
            for (Day15TaskMessage message : task.history()) {
                builder.append("  [").append(message.role()).append("] ")
                        .append(message.content()).append("\n");
            }
        }
        builder.append("Правила переходов: ")
                .append("нельзя перепрыгивать этапы — ")
                .append("реализация невозможна без утверждённого плана, ")
                .append("завершение возможно только после проверки; ")
                .append("переходи в следующее состояние только через разрешённый переход. ")
                .append("Продолжай работу с учётом текущего состояния. ")
                .append("Не повторяй объяснения, уже данные ранее: просто продолжай с того места, где остановился.");
        return builder.toString();
    }

    public static String transitionsHint(Day15Stage stage) {
        if (Day15StateMachine.isTerminal(stage)) {
            return "Задача завершена — переходы недоступны.";
        }
        List<Day15Stage> targets = Day15StateMachine.allowedTargets(stage);
        StringBuilder builder = new StringBuilder();
        for (Day15Stage target : targets) {
            builder.append(stage.name()).append(" → ").append(target.name()).append("\n");
        }
        return builder.toString().stripTrailing();
    }
}