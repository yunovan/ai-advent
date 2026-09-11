package com.yunovan.aiadvent.day10;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day10FileDialogStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsDialogWithFactsAndBranches() {
        Day10FileDialogStore store = new Day10FileDialogStore(tempDir);

        Day10Dialog dialog = store.create(Day10Strategy.FACTS, 6);
        Day10Dialog withMessages = dialog.withBranchMessages(
                dialog.activeBranchId(),
                List.of(ConversationMessage.user("Вопрос"), ConversationMessage.assistant("Ответ")));
        Day10Dialog withFacts = withMessages.withFacts(List.of(new Day10Fact("Цель", "собрать ТЗ", true)))
                .withCheckpoint(2)
                .withNewBranch(Day10Branch.fork(
                        "b2", "Ветка 2", withMessages.activeBranch(), 2))
                .finished("Итог", Instant.now());

        store.save(withFacts);

        Day10Dialog loaded = store.load(withFacts.id());
        assertThat(loaded).isNotNull();
        assertThat(loaded.strategy()).isEqualTo(Day10Strategy.FACTS);
        assertThat(loaded.windowSize()).isEqualTo(6);
        assertThat(loaded.facts()).extracting(Day10Fact::value).containsExactly("собрать ТЗ");
        assertThat(loaded.checkpointMessageIndex()).isEqualTo(2);
        assertThat(loaded.branches()).hasSize(2);
        assertThat(loaded.branches().get(1).id()).isEqualTo("b2");
        assertThat(loaded.isFinished()).isTrue();
        assertThat(loaded.activeMessages()).hasSize(2);
    }

    @Test
    void slidingWindowStrategyRoundTrips() {
        Day10FileDialogStore store = new Day10FileDialogStore(tempDir);

        Day10Dialog dialog = store.create(Day10Strategy.SLIDING_WINDOW, 3);
        store.save(dialog);

        Day10Dialog loaded = store.load(dialog.id());
        assertThat(loaded.strategy()).isEqualTo(Day10Strategy.SLIDING_WINDOW);
        assertThat(loaded.windowSize()).isEqualTo(3);
        assertThat(loaded.activeBranch().name()).isEqualTo("Основная");
    }

    @Test
    void loadReturnsNullForUnknownDialog() {
        Day10FileDialogStore store = new Day10FileDialogStore(tempDir);

        assertThat(store.load("no-such-id")).isNull();
    }

    @Test
    void finishedDialogsOnlyIncludeFinishedOnesAndAllDialogsAreSortedDescending() {
        Day10FileDialogStore store = new Day10FileDialogStore(tempDir);

        store.save(store.create(Day10Strategy.BRANCHING, 6));
        Day10Dialog finished = store.create(Day10Strategy.FACTS, 6).finished("Итог", Instant.now());
        store.save(finished);

        assertThat(store.finishedDialogs()).extracting(Day10Dialog::id)
                .containsExactly(finished.id());
        assertThat(store.allDialogs()).hasSize(2);
        assertThat(store.allDialogs().getFirst().finishedAt()).isNotNull();
    }
}