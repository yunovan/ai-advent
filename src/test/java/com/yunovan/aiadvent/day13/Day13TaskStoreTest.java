package com.yunovan.aiadvent.day13;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day13TaskStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void saveAndLoadRoundTrips() {
        Day13TaskStore store = new Day13TaskStore(tempDir);
        Day13Task task = Day13Task.create("Запуск нового сервиса");

        store.save(task);
        Day13Task loaded = store.load(task.id());

        assertThat(loaded).isNotNull();
        assertThat(loaded.id()).isEqualTo(task.id());
        assertThat(loaded.title()).isEqualTo("Запуск нового сервиса");
        assertThat(loaded.stage()).isEqualTo(Day13Stage.PLANNING);
        assertThat(loaded.step()).isEqualTo(1);
        assertThat(loaded.paused()).isFalse();
    }

    @Test
    void loadMissingReturnsNull() {
        Day13TaskStore store = new Day13TaskStore(tempDir);
        assertThat(store.load("no-such")).isNull();
    }

    @Test
    void allReturnsEmptyWhenDirMissing() {
        Day13TaskStore store = new Day13TaskStore(tempDir.resolve("missing"));
        assertThat(store.all()).isEmpty();
    }

    @Test
    void allListsSavedTasksNewestFirst() {
        Day13TaskStore store = new Day13TaskStore(tempDir);
        Day13Task first = store.save(Day13Task.create("Первая"));
        Day13Task second = store.save(Day13Task.create("Вторая"));

        List<Day13Task> all = store.all();

        assertThat(all).extracting(Day13Task::id).containsExactly(second.id(), first.id());
    }

    @Test
    void savePersistsAdvancedState() {
        Day13TaskStore store = new Day13TaskStore(tempDir);
        Day13Task task = Day13Task.create("Задача")
                .withStage(Day13Stage.EXECUTION)
                .withStep(3)
                .withPaused(true)
                .withExpectedAction("Проверить код");

        store.save(task);
        Day13Task loaded = store.load(task.id());

        assertThat(loaded.stage()).isEqualTo(Day13Stage.EXECUTION);
        assertThat(loaded.step()).isEqualTo(3);
        assertThat(loaded.paused()).isTrue();
        assertThat(loaded.expectedAction()).isEqualTo("Проверить код");
    }

    @Test
    void deleteRemovesTask() {
        Day13TaskStore store = new Day13TaskStore(tempDir);
        Day13Task task = store.save(Day13Task.create("Задача"));
        assertThat(store.delete(task.id())).isTrue();
        assertThat(store.load(task.id())).isNull();
    }
}