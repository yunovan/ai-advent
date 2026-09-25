package com.yunovan.aiadvent.day18;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day18StoreTest {

    @TempDir
    Path tempDir;

    private Day18Job job(String id, String feed, String status) {
        return new Day18Job(id, "collector", id, feed, status, null, 5, null, null,
                "2026-01-01T00:00:00Z", null, "2026-01-01T00:00:05Z");
    }

    @Test
    void persistsJobsAndSamplesAcrossInstances() {
        Day18Store store = new Day18Store(tempDir);
        store.saveJob(job("j-aaa", "events", "active"));
        store.saveSample(new Day18Sample("s-1", "j-aaa", "events", "ping",
                "2026-01-01T00:00:01Z", 1.0, "замер"));

        Day18Store reloaded = new Day18Store(tempDir);

        assertThat(reloaded.listJobs()).hasSize(1);
        assertThat(reloaded.listJobs().get(0).getId()).isEqualTo("j-aaa");
        assertThat(reloaded.listJobs().get(0).getStatus()).isEqualTo("active");
        assertThat(reloaded.allSamples()).hasSize(1);
        assertThat(reloaded.allSamples().get(0).payload()).isEqualTo("замер");
    }

    @Test
    void samplesForFiltersByFeedAndSince() {
        Day18Store store = new Day18Store(tempDir);
        store.saveSample(new Day18Sample("s-1", "j-1", "events", "ping",
                "2026-01-01T00:00:01Z", 1.0, "a"));
        store.saveSample(new Day18Sample("s-2", "j-1", "events", "ping",
                "2026-01-01T00:00:03Z", 2.0, "b"));
        store.saveSample(new Day18Sample("s-3", "j-2", "digest", "digest",
                "2026-01-01T00:00:04Z", 2.0, "c"));

        assertThat(store.samplesFor("events", null)).hasSize(2);
        assertThat(store.samplesFor("digest", null)).hasSize(1);
        assertThat(store.samplesFor(null, Instant.parse("2026-01-01T00:00:02Z"))).hasSize(2);
        assertThat(store.samplesFor("events", null).get(0).id()).isEqualTo("s-1");
    }

    @Test
    void saveSampleReplacesSampleWithSameId() {
        Day18Store store = new Day18Store(tempDir);
        store.saveSample(new Day18Sample("s-1", "j-1", "events", "ping",
                "2026-01-01T00:00:01Z", 1.0, "a"));
        store.saveSample(new Day18Sample("s-1", "j-1", "events", "ping",
                "2026-01-01T00:00:02Z", 2.0, "b"));

        assertThat(store.allSamples()).hasSize(1);
        assertThat(store.allSamples().get(0).payload()).isEqualTo("b");
    }
}