package com.yunovan.aiadvent.day02;

import java.util.List;

public final class Day02Constraints {

    public static final int MAX_TOKENS = 80;
    public static final String STOP_SEQUENCE = "<<<END>>>";
    public static final String SYSTEM_PROMPT =
            """
            Контролируй форму ответа.
            Формат: ровно 3 нумерованных пункта (1. 2. 3.), каждый — одно короткое предложение.
            Длина: весь ответ не длиннее 40 слов.
            Завершение: сразу после пункта 3 выведи маркер <<<END>>> и ничего больше.
            """;

    public static final List<String> STOP = List.of(STOP_SEQUENCE);

    private Day02Constraints() {
    }
}
