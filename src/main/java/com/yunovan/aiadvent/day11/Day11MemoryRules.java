package com.yunovan.aiadvent.day11;

import java.util.List;

public final class Day11MemoryRules {

    private static final List<String> PROFILE_MARKERS = List.of(
            "имя", "имени", "ник", "никнейм", "фио", "фамилия", "профиль",
            "предпочтение", "предпочтени", "любим", "любит", "люблю", "нравится",
            "язык", "город", "должность", "роль", "возраст", "имейл", "почта", "телефон");

    private static final List<String> TASK_MARKERS = List.of(
            "цель", "задач", "требован", "требован", "бюджет", "срок", "дедлайн",
            "огранич", "лимит", "стек", "технолог", "пожел", "условие", "критерий",
            "ненужно", "не нужно", "надо", "важно для задачи");

    private Day11MemoryRules() {
    }

    public static Day11MemoryLayer suggestLayer(String key) {
        if (key == null) {
            return Day11MemoryLayer.SHORT_TERM;
        }
        String normalized = key.toLowerCase();
        for (String marker : PROFILE_MARKERS) {
            if (normalized.contains(marker)) {
                return Day11MemoryLayer.LONG_TERM;
            }
        }
        for (String marker : TASK_MARKERS) {
            if (normalized.contains(marker)) {
                return Day11MemoryLayer.WORKING;
            }
        }
        return Day11MemoryLayer.SHORT_TERM;
    }
}