package com.yunovan.aiadvent.day35;

import org.springframework.stereotype.Component;

@Component
public class Day35ProblemGenerator {

    public Day35Problem generate(String topic, String level) {
        String tid = String.valueOf(System.nanoTime() & 0xFFFF);
        return switch (topic == null ? "algebra" : topic.toLowerCase()) {
            case "geometry" -> new Day35Problem("geo-" + tid, "Найдите площадь прямоугольника со сторонами 3 и 4.", "Площадь = a*b", level);
            case "fractions" -> new Day35Problem("frac-" + tid, "Вычислите 1/2 + 1/4.", "Приведите к общему знаменателю 4.", level);
            case "trigonometry" -> new Day35Problem("trig-" + tid, "Чему равен sin 30°?", "Значение табличное.", level);
            default -> new Day35Problem("alg-" + tid, "Решите уравнение 2x+5=15.", "Перенесите 5 и разделите на 2.", level);
        };
    }
}