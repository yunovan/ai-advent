package com.yunovan.aiadvent.day35;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class Day35ProblemGenerator {

    private final AtomicInteger counter = new AtomicInteger();

    public Generated generate(String topic, String level) {
        String tid = String.valueOf(counter.incrementAndGet());
        String normalized = topic == null ? "algebra" : topic.toLowerCase();
        return switch (normalized) {
            case "geometry" -> new Generated(
                    new Day35Problem("geo-" + tid,
                            "Найдите площадь прямоугольника со сторонами 3 см и 4 см.",
                            "Площадь прямоугольника равна произведению его сторон: S = a * b.",
                            level),
                    "12");
            case "fractions" -> new Generated(
                    new Day35Problem("frac-" + tid,
                            "Вычислите сумму дробей: 1/2 + 1/4.",
                            "Приведите дроби к общему знаменателю 4 и сложите числители.",
                            level),
                    "3/4");
            case "trigonometry" -> new Generated(
                    new Day35Problem("trig-" + tid,
                            "Чему равен sin 30°?",
                            "Это табличное значение из курса тригонометрии.",
                            level),
                    "0.5");
            default -> new Generated(
                    new Day35Problem("alg-" + tid,
                            "Решите линейное уравнение: 2x + 5 = 15.",
                            "Перенесите 5 в правую часть, затем разделите обе части на 2.",
                            level),
                    "5");
        };
    }

    public record Generated(Day35Problem problem, String expectedAnswer) {
    }
}
