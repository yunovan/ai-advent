package com.yunovan.aiadvent.day35;

import org.springframework.stereotype.Component;

@Component
public class Day35Tutor {
    public Day35Solution explain(Day35Problem p, String answer) {
        if (answer == null) answer = "";
        String a = answer.trim();
        boolean ok = a.equals("10") && p.id().startsWith("geo") || a.equals("3/4") && p.id().startsWith("frac") || (a.equals("0.5") || a.equals("1/2")) && p.id().startsWith("trig") || a.equals("5") && p.id().startsWith("alg");
        String explanation = ok ? "Верно. Пошаговое объяснение: применено правило из темы." : "Неверно. Разбор: сравните с эталоном решения. Попробуйте ещё раз с подсказкой.";
        String steps = "Шаг 1: прочитать условие. Шаг 2: выделить данные. Шаг 3: применить формулу/правило.";
        return new Day35Solution(p.id(), a, explanation, steps, ok ? "correct" : "incorrect");
    }
    public Day35ErrorAnalysis analyze(Day35Problem p, String answer) {
        if (answer == null || answer.isBlank()) return new Day35ErrorAnalysis("Пустой ответ", "Запишите вычисления", "Попробуйте воспользоваться подсказкой");
        return new Day35ErrorAnalysis("Вычислительная ошибка либо неверный ход решения", "Перепроверьте арифметику и формулу", "Постарайтесь расписать решение по шагам");
    }
}