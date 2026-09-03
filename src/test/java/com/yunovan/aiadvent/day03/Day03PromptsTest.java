package com.yunovan.aiadvent.day03;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Day03PromptsTest {

    @Test
    void stepByStepAppendsInstruction() {
        assertThat(Day03Prompts.stepByStep("Задача")).isEqualTo("Задача\n\nРешай пошагово.");
    }

    @Test
    void metaPromptAsksToWriteAPromptOnly() {
        String prompt = Day03Prompts.metaPromptRequest("Задача");
        assertThat(prompt).contains("Составь промпт");
        assertThat(prompt).contains("Задача");
        assertThat(prompt).doesNotContain("Решай пошагово");
    }

    @Test
    void expertsPromptNamesThreeRoles() {
        String prompt = Day03Prompts.experts("Задача");
        assertThat(prompt).contains("Аналитик");
        assertThat(prompt).contains("Инженер");
        assertThat(prompt).contains("Критик");
    }

    @Test
    void applyGeneratedPromptKeepsTask() {
        assertThat(Day03Prompts.applyGeneratedPrompt("Будь точным", "2+2"))
                .contains("Будь точным")
                .contains("2+2");
    }
}
