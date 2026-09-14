package com.yunovan.aiadvent.day11;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day11FileMemoryStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsEntryByLayer() {
        Day11FileMemoryStore store = new Day11FileMemoryStore(tempDir);

        store.save(new Day11MemoryEntry("Цель", "собрать ТЗ", Day11MemoryLayer.WORKING, "extracted", true, null));

        Day11MemoryEntry loaded = store.find(Day11MemoryLayer.WORKING, "Цель");
        assertThat(loaded).isNotNull();
        assertThat(loaded.value()).isEqualTo("собрать ТЗ");
        assertThat(loaded.layer()).isEqualTo(Day11MemoryLayer.WORKING);
        assertThat(loaded.isCandidate()).isTrue();
    }

    @Test
    void layersAreStoredSeparately() {
        Day11FileMemoryStore store = new Day11FileMemoryStore(tempDir);
        Day11MemoryEntry common = new Day11MemoryEntry("Стек", "Java", Day11MemoryLayer.WORKING, "manual", false, null);
        Day11MemoryEntry profile = new Day11MemoryEntry("Стек", "Java 21", Day11MemoryLayer.LONG_TERM, "manual", false, null);

        store.save(common);
        store.save(profile);

        assertThat(store.find(Day11MemoryLayer.WORKING, "Стек").value()).isEqualTo("Java");
        assertThat(store.find(Day11MemoryLayer.LONG_TERM, "Стек").value()).isEqualTo("Java 21");
        assertThat(store.all()).hasSize(2);
    }

    @Test
    void deleteRemovesOnlyFromRequestedLayer() {
        Day11FileMemoryStore store = new Day11FileMemoryStore(tempDir);
        store.save(new Day11MemoryEntry("Имя", "Ася", Day11MemoryLayer.SHORT_TERM, "extracted", true, null));
        store.save(new Day11MemoryEntry("Имя", "Ася", Day11MemoryLayer.LONG_TERM, "manual", false, null));

        assertThat(store.delete(Day11MemoryLayer.SHORT_TERM, "Имя")).isTrue();
        assertThat(store.all(Day11MemoryLayer.SHORT_TERM)).isEmpty();
        assertThat(store.find(Day11MemoryLayer.LONG_TERM, "Имя")).isNotNull();
    }

    @Test
    void findReturnsNullForMissingEntry() {
        Day11FileMemoryStore store = new Day11FileMemoryStore(tempDir);

        assertThat(store.find(Day11MemoryLayer.WORKING, "нет-такого")).isNull();
    }

    @Test
    void sanitizeNormalizesKeys() {
        assertThat(Day11FileMemoryStore.sanitize("Имя Пользователя!")).isEqualTo("имя_пользователя");
        assertThat(Day11FileMemoryStore.sanitize("  ")).isEqualTo("entry");
    }
}