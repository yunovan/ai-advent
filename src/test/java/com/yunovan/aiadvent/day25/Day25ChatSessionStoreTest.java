package com.yunovan.aiadvent.day25;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day25ChatSessionStoreTest {

    @TempDir
    Path storeDir;

    private Day25ChatSessionStore store(int maxSessions) {
        return new Day25ChatSessionStore(storeDir, maxSessions);
    }

    private Day25ChatSessionStore.Session session(String id, int turn) {
        return new Day25ChatSessionStore.Session(id,
                List.of(new Day25Message("user", "вопрос " + turn, turn),
                        new Day25Message("assistant", "ответ " + turn, turn)),
                new Day25TaskMemory("разобраться в эмбеддингах", turn, turn,
                        List.of("а как их считают"), List.of("только по документации"),
                        List.of("эмбеддинг")));
    }

    @Test
    void historyAndMemorySurviveRestart() {
        store(10).save(session("demo", 3));

        Day25ChatSessionStore reopened = store(10);

        Day25ChatSessionStore.Session loaded = reopened.get("demo");
        assertThat(loaded).isNotNull();
        assertThat(loaded.messages()).hasSize(2);
        assertThat(loaded.messages().getLast().text()).isEqualTo("ответ 3");
        assertThat(loaded.memory().goal()).isEqualTo("разобраться в эмбеддингах");
        assertThat(loaded.memory().constraints()).containsExactly("только по документации");
        assertThat(loaded.memory().clarifications()).containsExactly("а как их считают");
    }

    @Test
    void sessionsAreIsolatedAndOldestIsEvicted() {
        Day25ChatSessionStore first = store(2);
        first.save(session("one", 1));
        first.save(session("two", 1));
        first.save(session("three", 1));

        Day25ChatSessionStore reopened = store(2);
        assertThat(reopened.size()).isEqualTo(2);
        assertThat(reopened.get("one")).isNull();
        assertThat(reopened.get("two")).isNotNull();
        assertThat(reopened.get("three")).isNotNull();
        assertThat(reopened.all()).extracting(Day25ChatSessionStore.Session::sessionId)
                .containsExactly("two", "three");
    }

    @Test
    void resetRemovesOnlyTargetSession() {
        Day25ChatSessionStore first = store(10);
        first.save(session("demo", 1));
        first.save(session("other", 1));

        first.reset("demo");

        Day25ChatSessionStore reopened = store(10);
        assertThat(reopened.get("demo")).isNull();
        assertThat(reopened.get("other")).isNotNull();
    }

    @Test
    void corruptedStoreFallsBackToEmptyState() throws Exception {
        Files.createDirectories(storeDir);
        Files.writeString(storeDir.resolve("sessions.json"), "{not json");

        Day25ChatSessionStore reopened = store(10);

        assertThat(reopened.size()).isZero();
        assertThat(reopened.get("demo")).isNull();
    }
}
