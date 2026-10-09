package com.yunovan.aiadvent.day27;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day27ChatSessionStoreTest {

    @TempDir
    Path dir;

    private static Day27Message message(String role, String text, int turn) {
        return new Day27Message(role, text, turn, 0, 0, 0, 0.0);
    }

    @Test
    void saveAndReloadKeepsHistoryAcrossInstances() {
        Day27ChatSessionStore store = new Day27ChatSessionStore(dir, 5);
        store.save(new Day27ChatSessionStore.Session("web",
                List.of(message("user", "Привет", 1), message("assistant", "Здравствуйте!", 1))));

        Day27ChatSessionStore reloaded = new Day27ChatSessionStore(dir, 5);

        assertThat(reloaded.size()).isEqualTo(1);
        assertThat(reloaded.get("web").messages()).hasSize(2);
        assertThat(reloaded.get("web").messages().getLast().text()).isEqualTo("Здравствуйте!");
    }

    @Test
    void evictsOldestSessionBeyondMaxSessions() {
        Day27ChatSessionStore store = new Day27ChatSessionStore(dir, 2);
        store.save(new Day27ChatSessionStore.Session("first", List.of(message("user", "1", 1))));
        store.save(new Day27ChatSessionStore.Session("second", List.of(message("user", "2", 1))));
        store.save(new Day27ChatSessionStore.Session("third", List.of(message("user", "3", 1))));

        assertThat(store.size()).isEqualTo(2);
        assertThat(store.get("first")).isNull();
        assertThat(store.get("second")).isNotNull();
        assertThat(store.get("third")).isNotNull();
    }

    @Test
    void resetRemovesOnlyGivenSession() {
        Day27ChatSessionStore store = new Day27ChatSessionStore(dir, 5);
        store.save(new Day27ChatSessionStore.Session("web", List.of(message("user", "1", 1))));
        store.save(new Day27ChatSessionStore.Session("cli", List.of(message("user", "2", 1))));

        store.reset("web");

        assertThat(store.get("web")).isNull();
        assertThat(store.get("cli")).isNotNull();
        assertThat(new Day27ChatSessionStore(dir, 5).get("web")).isNull();
    }

    @Test
    void corruptStoreFileStartsEmptyInsteadOfFailing() throws IOException {
        Files.writeString(dir.resolve("sessions.json"), "это не JSON {");

        Day27ChatSessionStore store = new Day27ChatSessionStore(dir, 5);

        assertThat(store.size()).isZero();
    }
}
