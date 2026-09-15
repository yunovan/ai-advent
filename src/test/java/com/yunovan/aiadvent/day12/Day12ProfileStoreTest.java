package com.yunovan.aiadvent.day12;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day12ProfileStoreTest {

    @TempDir
    Path tempDir;

    private Day12ProfileStore store;

    @BeforeEach
    void setUp() {
        store = new Day12ProfileStore(tempDir.resolve("profiles"));
    }

    @Test
    void createSavesAndFindsProfile() {
        Day12Profile profile = store.create("Ася", "кратко", "списки", List.of("без эмодзи"), "аналитик");

        assertThat(profile.id()).isEqualTo("asya");
        Day12Profile loaded = store.find("asya");
        assertThat(loaded).isNotNull();
        assertThat(loaded.name()).isEqualTo("Ася");
        assertThat(loaded.restrictions()).containsExactly("без эмодзи");
    }

    @Test
    void findByNameIgnoresCaseAndFinds() {
        store.create("Менеджер", "формально", "отчёт", List.of(), "руководитель");

        Day12Profile profile = store.findByName("менедЖер");

        assertThat(profile).isNotNull();
        assertThat(profile.id()).isEqualTo("menedzher");
    }

    @Test
    void findByNameUnknownReturnsNull() {
        assertThat(store.findByName("Нет такого")).isNull();
    }

    @Test
    void findUnknownIdReturnsNull() {
        assertThat(store.find("missing")).isNull();
    }

    @Test
    void deleteRemovesProfile() {
        store.create("Ася", "кратко", "списки", List.of(), "аналитик");

        assertThat(store.delete("asya")).isTrue();
        assertThat(store.find("asya")).isNull();
        assertThat(store.delete("asya")).isFalse();
    }

    @Test
    void duplicateNamesGetUniqueIds() {
        Day12Profile first = store.create("Разработчик", "", "", List.of(), "");
        Day12Profile second = store.create("Разработчик", "", "", List.of(), "");

        assertThat(first.id()).isEqualTo("razrabotchik");
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(second.id()).startsWith("razrabotchik-");
    }

    @Test
    void seedIfEmptyCreatesThreeSampleProfiles() {
        store.seedIfEmpty();

        assertThat(store.all()).hasSize(3);
        assertThat(store.all()).extracting(Day12Profile::name)
                .containsExactly("Ася", "Менеджер", "Разработчик");
    }

    @Test
    void seedIfEmptyDoesNotDuplicateWhenProfilesExist() {
        store.seedIfEmpty();
        store.seedIfEmpty();

        assertThat(store.all()).hasSize(3);
    }
}