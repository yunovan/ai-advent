package com.yunovan.aiadvent.day13;

public final class Day13TaskPrompt {

    private Day13TaskPrompt() {
    }

    public static String taskBlock(Day13Task task) {
        StringBuilder builder = new StringBuilder();
        builder.append("Состояние задачи (конечный автомат):\n");
        builder.append("- Задача: ").append(task.title()).append("\n");
        builder.append("- Этап: ").append(task.stage().display())
                .append(task.stage() == Day13Stage.DONE ? "" : " (цепочка: планирование → выполнение → проверка → готово)")
                .append("\n");
        builder.append("- Текущий шаг: ").append(task.step()).append("\n");
        builder.append("- Ожидаемое действие: ").append(task.expectedAction()).append("\n");
        if (!task.notes().isEmpty()) {
            builder.append("Заметки по задаче:\n");
            for (String note : task.notes()) {
                builder.append("  - ").append(note).append("\n");
            }
        }
        if (!task.history().isEmpty()) {
            builder.append("История диалога:\n");
            for (Day13TaskMessage message : task.history()) {
                builder.append("  [").append(message.role()).append("] ")
                        .append(message.content()).append("\n");
            }
        }
        builder.append("Продолжай работу с учётом текущего этапа, шага и ожидаемого действия. ")
                .append("Не повторяй объяснения, уже данные ранее: просто продолжай с того места, где остановился.");
        return builder.toString();
    }
}