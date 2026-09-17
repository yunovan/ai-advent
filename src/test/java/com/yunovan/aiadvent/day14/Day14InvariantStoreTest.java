package com.yunovan.aiadvent.day14;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day14InvariantStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTrips() {
        Day14InvariantStore store = new Day14InvariantStore(tempDir);
        Day14Invariant invariant = Day14Invariant.create(Day14Category.STACK, "База данных", "только PostgreSQL");

        store.save(invariant);
        Day14Invariant loaded = store.load(invariant.id());

        assertThat(loaded).isNotNull();
        assertThat(loaded.id()).isEqualTo(invariant.id());
        assertThat(loaded.category()).isEqualTo(Day14Category.STACK);
        assertThat(loaded.title()).isEqualTo("База данных");
        assertThat(loaded.description()).isEqualTo("только PostgreSQL");
        assertThat(loaded.active()).isTrue();
    }

    @Test
    void loadMissingReturnsNull() {
        Day14InvariantStore store = new Day14InvariantStore(tempDir);
        assertThat(store.load("no-such")).isNull();
    }

    @Test
    void allReturnsEmptyWhenDirMissing() {
        Day14InvariantStore store = new Day14InvariantStore(tempDir.resolve("missing"));
        assertThat(store.all()).isEmpty();
    }

    @Test
    void allListsSavedNewestFirst() {
        Day14InvariantStore store = new Day14InvariantStore(tempDir);
        Day14Invariant first = store.save(Day14Invariant.create(Day14Category.STACK, "Первая", "описание"));
        Day14Invariant second = store.save(Day14Invariant.create(Day14Category.BUSINESS, "Вторая", "описание"));

        List<Day14Invariant> all = store.all();

        assertThat(all).extracting(Day14Invariant::id).containsExactly(second.id(), first.id());
    }

    @Test
    void savePersistsInactiveState() {
        Day14InvariantStore store = new Day14InvariantStore(tempDir);
        Day14Invariant invariant = store.save(Day14Invariant.create(Day14Category.DECISION, "Стек", "Java 21").withActive(false));

        Day14Invariant loaded = store.load(invariant.id());

        assertThat(loaded.active()).isFalse();
    }

    @Test
    void deleteRemovesInvariant() {
        Day14InvariantStore store = new Day14InvariantStore(tempDir);
        Day14Invariant invariant = store.save(Day14Invariant.create(Day14Category.STACK, "База", "PostgreSQL"));
        assertThat(store.delete(invariant.id())).isTrue();
        assertThat(store.load(invariant.id())).isNull();
    }
}