package com.yunovan.aiadvent.day23;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Day23QueryRewriterTest {

    @Test
    void rewriteKeepsSingleOccurrenceOfRepeatedWords() {
        Day23QueryRewriter.Rewrite rewrite = Day23QueryRewriter.rewrite(
                "эмбеддинги документов эмбеддинги документов эмбеддинги");

        assertThat(List.of(rewrite.rewritten().split(" ")))
                .doesNotHaveDuplicates()
                .contains("эмбеддинги", "документов");
        assertThat(rewrite.expansions()).isNotEmpty();
    }

    @Test
    void rewriteHandlesRepeatedWordsWithRules() {
        Day23QueryRewriter.Rewrite rewrite = Day23QueryRewriter.rewrite(
                "память память память MCP память");

        assertThat(rewrite.rewritten()).startsWith("память MCP");
        assertThat(List.of(rewrite.rewritten().split(" ")))
                .doesNotHaveDuplicates();
        assertThat(rewrite.expansions()).isNotEmpty();
    }

    @Test
    void rewriteOfBlankQuestionStaysBlank() {
        assertThat(Day23QueryRewriter.rewrite(null).rewritten()).isEmpty();
        assertThat(Day23QueryRewriter.normalize("  эмбеддинги   документов "))
                .isEqualTo("эмбеддинги документов");
    }
}
