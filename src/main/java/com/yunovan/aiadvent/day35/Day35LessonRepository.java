package com.yunovan.aiadvent.day35;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class Day35LessonRepository {
    private final List<Day35Lesson> lessons = List.of(
        new Day35Lesson("algebra", "Алгебра", "5-7", "Линейные уравнения, действия с числами"),
        new Day35Lesson("geometry", "Геометрия", "5-7", "Площади и периметры фигур"),
        new Day35Lesson("fractions", "Дроби", "5-6", "Сложение и вычитание обыкновенных дробей")
    );
    public List<Day35Lesson> all() { return lessons; }
}