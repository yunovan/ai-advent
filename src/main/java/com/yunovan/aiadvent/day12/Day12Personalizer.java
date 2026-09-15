package com.yunovan.aiadvent.day12;

import java.util.List;

public final class Day12Personalizer {

    private Day12Personalizer() {
    }

    public static String block(Day12Profile profile) {
        StringBuilder builder = new StringBuilder("Профиль пользователя:\n");
        builder.append("- Имя: ").append(profile.name()).append("\n");
        builder.append("- Стиль: ").append(valueOr(profile.style(), "обычный")).append("\n");
        builder.append("- Формат: ").append(valueOr(profile.format(), "обычный текст")).append("\n");
        if (!profile.restrictions().isEmpty()) {
            builder.append("- Ограничения: ").append(String.join("; ", profile.restrictions())).append("\n");
        }
        if (!profile.notes().isBlank()) {
            builder.append("- Дополнительно: ").append(profile.notes()).append("\n");
        }
        builder.append("Учитывай этот профиль в каждом ответе: соблюдай стиль, формат и ограничения.");
        return builder.toString();
    }

    public static List<String> defaultRestrictions(String name) {
        return switch (name.toLowerCase()) {
            case "ася" -> List.of("без жаргона", "без эмодзи", "только русский");
            case "manager", "менеджер" -> List.of("без сокращений", "минимум канцелярита");
            case "dev", "разработчик" -> List.of("без повторов", "без маркетинговых формулировок");
            default -> List.of();
        };
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}