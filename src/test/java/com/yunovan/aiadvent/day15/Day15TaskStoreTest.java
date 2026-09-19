package com.yunovan.aiadvent.day15;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day15TaskStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTrips() {
        Day15TaskStore store = new Day15TaskStore(tempDir);
        Day15Task task = Day15Task.create("Запуск нового сервиса");

        store.save(task);
        Day15Task loaded = store.load(task.id());

        assertThat(loaded).isNotNull();
        assertThat(loaded.id()).isEqualTo(task.id());
        assertThat(loaded.title()).isEqualTo("Запуск нового сервиса");
        assertThat(loaded.stage()).isEqualTo(Day15Stage.PLANNING);
        assertThat(loaded.paused()).isFalse();
    }

    @Test
    void loadMissingReturnsNull() {
        Day15TaskStore store = new Day15TaskStore(tempDir);
        assertThat(store.load("no-such")).isNull();
    }

    @Test
    void allReturnsEmptyWhenDirMissing() {
        Day15TaskStore store = new Day15TaskStore(tempDir.resolve("missing"));
        assertThat(store.all()).isEmpty();
    }

    @Test
    void allListsSavedTasksNewestFirst() {
        Day15TaskStore store = new Day15TaskStore(tempDir);
        Day15Task first = store.save(Day15Task.create("Первая"));
        Day15Task second = store.save(Day15Task.create("Вторая"));

        List<Day15Task> all = store.all();

        assertThat(all).extracting(Day15Task::id).containsExactly(second.id(), first.id());
    }

    @Test
    void savePersistsControlledStage() {
        Day15TaskStore store = new Day15TaskStore(tempDir);
        Day15Task task = Day15Task.create("Задача")
                .withStage(Day15Stage.PLAN_APPROVED)
                .withPaused(true)
                .withNote("План утверждён");

        store.save(task);
        Day15Task loaded = store.load(task.id());

        assertThat(loaded.stage()).isEqualTo(Day15Stage.PLAN_APPROVED);
        assertThat(loaded.paused()).isTrue();
        assertThat(loaded.notes()).containsExactly("План утверждён");
    }

    @Test
    void deleteRemovesTask() {
        Day15TaskStore store = new Day15TaskStore(tempDir);
        Day15Task task = store.save(Day15Task.create("Задача"));
        assertThat(store.delete(task.id())).isTrue();
        assertThat(store.load(task.id())).isNull();
    }
}