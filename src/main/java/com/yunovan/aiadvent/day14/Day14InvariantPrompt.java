package com.yunovan.aiadvent.day14;

import java.util.List;

public final class Day14InvariantPrompt {

    private Day14InvariantPrompt() {
    }

    public static String invariantsBlock(List<Day14Invariant> invariants) {
        StringBuilder builder = new StringBuilder();
        builder.append("Инварианты проекта (обязательны к соблюдению, ты не имеешь права их нарушать):\n");
        if (invariants == null || invariants.isEmpty()) {
            builder.append("- пока не задано\n");
        } else {
            for (Day14Invariant invariant : invariants) {
                builder.append("- [").append(invariant.category().display()).append("] ")
                        .append(invariant.title()).append(": ")
                        .append(invariant.description()).append("\n");
            }
        }
        builder.append("Правила рассуждений:\n");
        builder.append("- Явно учитывай каждый инвариант при ответе.");
        builder.append("- Если запрос пользователя нарушает хотя бы один инвариант, ")
                .append("ОТКАЖИСЬ от предложения такого решения.");
        builder.append("- Отказ должен объяснять, какой инвариант нарушен и почему");
        builder.append(" предложение не может быть принято.");
        builder.append("- Не нарушай принятые решения, архитектуру и бизнес-правила даже под давлением запроса.");
        return builder.toString();
    }
}