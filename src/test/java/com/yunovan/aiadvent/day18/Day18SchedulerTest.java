package com.yunovan.aiadvent.day18;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day18SchedulerTest {

    @TempDir
    Path tempDir;

    private Day18SchedulerService scheduler;
    private Day18Store store;

    @BeforeEach
    void setUp() {
        store = new Day18Store(tempDir);
        scheduler = new Day18SchedulerService(store,
                new Day18Properties(0, "/mcp", "ai-advent-scheduler-mcp", "0.1.0", "dir", 50L),
                new Day18HttpProbe());
        scheduler.start();
    }

    @AfterEach
    void tearDown() {
        scheduler.stop();
    }

    @Test
    void reminderFiresExactlyOnce() {
        Day18Job job = scheduler.addReminder("выпить чай", 1);

        await(() -> {
            Day18Job current = store.findById(job.getId());
            return current != null && "done".equals(current.getStatus());
        });

        Day18Job fired = store.findById(job.getId());
        assertThat(fired.getRunCount()).isEqualTo(1);
        assertThat(fired.getNextRunAt()).isNull();
        List<Day18Sample> reminders = store.samplesFor("reminders", null);
        assertThat(reminders).hasSize(1);
        assertThat(reminders.get(0).kind()).isEqualTo("reminder");
        assertThat(reminders.get(0).payload()).contains("выпить чай");

        sleep(700);
        assertThat(store.findById(job.getId()).getRunCount()).isEqualTo(1);
        assertThat(store.samplesFor("reminders", null)).hasSize(1);
    }

    @Test
    void collectorCollectsSamplesOnSchedule() {
        Day18Job job = scheduler.addCollector("events", 1, null, null);

        await(() -> store.samplesFor("events", null).size() >= 2);

        Day18Job current = store.findById(job.getId());
        assertThat(current.getRunCount()).isGreaterThanOrEqualTo(2);
        assertThat(current.getStatus()).isEqualTo("active");
        assertThat(store.samplesFor("events", null).get(0).kind()).isEqualTo("ping");
        assertThat(store.samplesFor("events", null).get(0).payload())
                .contains("мок-замер №").contains("events");
    }

    @Test
    void collectorWithSourceFeedWritesDigest() {
        Day18Job events = scheduler.addCollector("events", 60, null, null);
        scheduler.runNow(events.getId());
        scheduler.runNow(events.getId());

        Day18Job digestJob = scheduler.addCollector("digest", 60, null, "events");
        scheduler.runNow(digestJob.getId());
        scheduler.runNow(digestJob.getId());

        List<Day18Sample> digests = store.samplesFor("digest", null);
        assertThat(digests).hasSize(2);
        assertThat(digests.get(0).kind()).isEqualTo("digest");
        assertThat(digests.get(0).value()).isEqualTo(2.0);
        assertThat(digests.get(0).payload()).contains("Сводка по 'events'").contains("всего событий 2");
    }

    @Test
    void runNowExecutesJobImmediately() {
        Day18Job job = scheduler.addCollector("events", 60, null, null);
        assertThat(store.samplesFor("events", null)).isEmpty();

        Day18Job after = scheduler.runNow(job.getId());

        assertThat(after.getRunCount()).isEqualTo(1);
        assertThat(store.findById(job.getId()).getRunCount()).isEqualTo(1);
        assertThat(store.samplesFor("events", null)).hasSize(1);
        assertThat(store.samplesFor("events", null).get(0).value()).isEqualTo(1.0);
    }

    @Test
    void schedulePersistsAcrossRestart() {
        Day18Job job = scheduler.addCollector("events", 60, null, null);
        scheduler.runNow(job.getId());

        scheduler.stop();
        Day18Store reloaded = new Day18Store(tempDir);
        Day18SchedulerService restarted = new Day18SchedulerService(reloaded,
                new Day18Properties(0, "/mcp", "ai-advent-scheduler-mcp", "0.1.0", "dir", 50L),
                new Day18HttpProbe());
        restarted.start();
        try {
            assertThat(reloaded.listJobs()).hasSize(1);
            assertThat(reloaded.listJobs().get(0).getRunCount()).isEqualTo(1);
            assertThat(reloaded.samplesFor("events", null)).hasSize(1);
        } finally {
            restarted.stop();
        }
    }

    @Test
    void summaryAggregatesCollectedSamples() {
        Day18Job job = scheduler.addCollector("events", 60, null, null);
        scheduler.runNow(job.getId());
        scheduler.runNow(job.getId());

        Day18Summary summary = scheduler.summary("events", null);

        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.avgValue()).isEqualTo(1.5);
        assertThat(summary.minValue()).isEqualTo(1.0);
        assertThat(summary.maxValue()).isEqualTo(2.0);
        assertThat(summary.recent()).hasSize(2);
        assertThat(summary.lastPayload()).contains("мок-замер");
    }

    private static void await(BooleanSupplier condition) {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            sleep(100);
        }
        throw new AssertionError("Условие не выполнилось за 10 секунд");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}