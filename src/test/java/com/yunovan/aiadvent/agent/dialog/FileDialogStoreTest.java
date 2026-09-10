package com.yunovan.aiadvent.agent.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileDialogStoreTest {

    @TempDir
    Path tempDir;

    private FileDialogStore store() {
        return new FileDialogStore(tempDir);
    }

    @Test
    void createSavesAnEmptyDialogOnDisk() {
        Dialog dialog = store().create();

        assertThat(dialog.id()).isNotBlank();
        assertThat(dialog.messages()).isEmpty();
        assertThat(Files.exists(tempDir.resolve(dialog.id() + ".json"))).isTrue();
    }

    @Test
    void loadReturnsNullForUnknownDialog() {
        assertThat(store().load("unknown")).isNull();
    }

    @Test
    void saveThenLoadRoundTripsDialog() {
        FileDialogStore store = store();
        Dialog saved = new Dialog(
                "abc",
                Instant.parse("2026-09-08T10:00:00Z"),
                Instant.parse("2026-09-08T10:05:00Z"),
                "Итог: говорили про небо.",
                null, 0,
                List.of(ConversationMessage.user("Почему небо синее?"), ConversationMessage.assistant("Из-за рассеяния света.")));

        store.save(saved);
        Dialog loaded = store.load("abc");

        assertThat(loaded.id()).isEqualTo("abc");
        assertThat(loaded.createdAt()).isEqualTo(Instant.parse("2026-09-08T10:00:00Z"));
        assertThat(loaded.finishedAt()).isEqualTo(Instant.parse("2026-09-08T10:05:00Z"));
        assertThat(loaded.summary()).isEqualTo("Итог: говорили про небо.");
        assertThat(loaded.isFinished()).isTrue();
        assertThat(loaded.messages()).containsExactly(
                ConversationMessage.user("Почему небо синее?"),
                ConversationMessage.assistant("Из-за рассеяния света."));
    }

    @Test
    void finishedDialogsReturnsOnlyFinishedSortedNewestFirst() {
        FileDialogStore store = store();
        Dialog older = Dialog.create().finished("старый итог", Instant.parse("2026-09-08T09:00:00Z"));
        store.save(older);
        Dialog newer = Dialog.create().finished("новый итог", Instant.parse("2026-09-08T10:00:00Z"));
        store.save(newer);
        Dialog unfinished = Dialog.create();
        store.save(unfinished);

        List<Dialog> finished = store.finishedDialogs();

        assertThat(finished).extracting(Dialog::id).containsExactly(newer.id(), older.id());
        assertThat(finished.getFirst().summary()).isEqualTo("новый итог");
    }

    @Test
    void finishedDialogsIgnoresNonDialogFiles() throws Exception {
        FileDialogStore store = store();
        store.save(Dialog.create().finished("итог", Instant.parse("2026-09-08T09:00:00Z")));
        Files.writeString(tempDir.resolve("note.txt"), "не диалог");

        assertThat(store.finishedDialogs()).hasSize(1);
    }
}