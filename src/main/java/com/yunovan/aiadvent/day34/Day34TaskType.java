package com.yunovan.aiadvent.day34;

import java.util.Locale;

public enum Day34TaskType {

    FIND_USAGES("usages", "Поиск использований компонента/API"),
    GENERATE_ADR("adr", "Генерация ADR"),
    GENERATE_CHANGELOG("changelog", "Генерация changelog"),
    GENERATE_README("readme", "Генерация README"),
    CHECK_INVARIANTS("invariants", "Проверка инвариантов файлов"),
    UPDATE_DOC("update", "Обновление документации по коду");

    private final String code;
    private final String label;

    Day34TaskType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public boolean generatesDocument() {
        return this == GENERATE_ADR || this == GENERATE_CHANGELOG || this == GENERATE_README;
    }

    public static Day34TaskType fromCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (Day34TaskType type : values()) {
            if (type.code.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return type;
            }
        }
        return switch (normalized) {
            case "find_usages", "find", "usage", "использование", "использования" -> FIND_USAGES;
            case "generate_adr", "decision" -> GENERATE_ADR;
            case "generate_changelog", "changes" -> GENERATE_CHANGELOG;
            case "generate_readme", "generate" -> GENERATE_README;
            case "check_invariants", "check", "invariant", "rules" -> CHECK_INVARIANTS;
            case "update_doc", "sync", "docs", "documentation" -> UPDATE_DOC;
            default -> null;
        };
    }
}
