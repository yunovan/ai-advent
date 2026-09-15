package com.yunovan.aiadvent.day12;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class Day12ProfileTest {

    @Test
    void trimsFieldsAndDropsBlankRestrictions() {
        Day12Profile profile = new Day12Profile(
                " asya ", " Ася ", " стиль ", " формат ",
                Arrays.asList(" без жаргона ", " ", null, "без эмодзи"), " заметки ", null);

        assertThat(profile.id()).isEqualTo("asya");
        assertThat(profile.name()).isEqualTo("Ася");
        assertThat(profile.restrictions()).containsExactly("без жаргона", "без эмодзи");
    }

    @Test
    void usableRequiresIdAndName() {
        assertThat(new Day12Profile("id", "Имя", "", "", List.of(), "", null).usable()).isTrue();
        assertThat(new Day12Profile("", "Имя", "", "", List.of(), "", null).usable()).isFalse();
        assertThat(new Day12Profile("id", "", "", "", List.of(), "", null).usable()).isFalse();
    }

    @Test
    void displayJoinsNameStyleFormatAndRestrictions() {
        Day12Profile profile = new Day12Profile(
                "manager", "Менеджер", "развёрнуто", "отчёт", List.of("без сокращений"), "", null);

        assertThat(profile.display()).isEqualTo(
                "Менеджер · развёрнуто · отчёт · без: без сокращений");
    }

    @Test
    void personalizerBlockListsProfileAndDemandsCompliance() {
        Day12Profile profile = new Day12Profile(
                "asya", "Ася", "кратко", "списки", List.of("без эмодзи"), "аналитик", null);

        String block = Day12Personalizer.block(profile);

        assertThat(block).contains("Профиль пользователя:");
        assertThat(block).contains("- Имя: Ася");
        assertThat(block).contains("- Стиль: кратко");
        assertThat(block).contains("- Формат: списки");
        assertThat(block).contains("- Ограничения: без эмодзи");
        assertThat(block).contains("- Дополнительно: аналитик");
        assertThat(block).contains("Учитывай этот профиль в каждом ответе");
    }

    @Test
    void personalizerUsesFallbacksForBlankFields() {
        String block = Day12Personalizer.block(new Day12Profile("x", "Имя", "", "", List.of(), "", null));

        assertThat(block).contains("- Стиль: обычный");
        assertThat(block).contains("- Формат: обычный текст");
        assertThat(block).doesNotContain("Ограничения");
        assertThat(block).doesNotContain("Дополнительно");
    }

    @Test
    void defaultRestrictionsByKnownName() {
        assertThat(Day12Personalizer.defaultRestrictions("Ася")).contains("без эмодзи");
        assertThat(Day12Personalizer.defaultRestrictions("Менеджер")).contains("без сокращений");
        assertThat(Day12Personalizer.defaultRestrictions("Разработчик")).contains("без повторов");
        assertThat(Day12Personalizer.defaultRestrictions("Неизвестный")).isEmpty();
    }
}