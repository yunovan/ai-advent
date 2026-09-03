package com.yunovan.aiadvent.day04;

import java.util.List;

public final class Day04Temperatures {

    public static final String DEFAULT_PROMPT =
            "Назови столицу Франции. Затем придумай необычный слоган для туристического плаката этого города.";

    public static final List<Double> VALUES = List.of(0.0, 0.7, 1.2);

    public static final String CONCLUSIONS =
            """
            0.0 — точность высокая, креативность низкая, разнообразие минимальное. Лучше для фактов, кода, SQL, извлечения данных.
            0.7 — баланс точности и живости. Лучше для чата, объяснений, писем.
            1.2 — точность падает, креативность и разнообразие растут. Лучше для идей, слоганов, художественного текста; факты стоит проверять.
            """
                    .trim();

    private Day04Temperatures() {
    }

    public static String bestFor(double temperature) {
        if (temperature <= 0.0) {
            return "Факты, код, классификация, стабильный ответ";
        }
        if (temperature < 1.0) {
            return "Обычный диалог, объяснения, умеренная креативность";
        }
        return "Идеи, названия, мозговой штурм — выше риск ошибок";
    }
}
