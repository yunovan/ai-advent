package com.yunovan.aiadvent.agent.dialog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DialogContextTest {

    private final DialogContext context = new DialogContext();

    @Test
    void emptyMemorySaysthereIsNone() {
        assertThat(context.systemPrompt(List.of())).contains("пока нет");
    }

    @Test
    void includesFinishedDialogSummaries() {
        Dialog dialog = new Dialog(
                "d1", Instant.now(), Instant.now(), "Говорили про небо и море", List.of());

        String prompt = context.systemPrompt(List.of(dialog));

        assertThat(prompt).contains("Говорили про небо и море");
        assertThat(prompt).contains("прошлых завершённых");
    }
}