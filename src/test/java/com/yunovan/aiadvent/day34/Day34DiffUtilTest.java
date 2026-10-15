package com.yunovan.aiadvent.day34;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Day34DiffUtilTest {

    @Test
    void returnsEmptyForIdenticalContent() {
        assertThat(Day34DiffUtil.unified("a.md", "same\n", "same\n")).isEmpty();
    }

    @Test
    void rendersNewFileDiff() {
        String diff = Day34DiffUtil.unified("docs/adr.md", "", "# ADR\nContext\n");

        assertThat(diff).contains("--- /dev/null");
        assertThat(diff).contains("+++ b/docs/adr.md");
        assertThat(diff).contains("+# ADR");
        assertThat(diff).contains("+Context");
    }

    @Test
    void rendersChangedLines() {
        String diff = Day34DiffUtil.unified("a.md", "keep\nalpha\n", "keep\nbeta\n");

        assertThat(diff).contains("--- a/a.md");
        assertThat(diff).contains("+++ b/a.md");
        assertThat(diff).contains("-alpha");
        assertThat(diff).contains("+beta");
        assertThat(diff).contains("@@");
    }

    @Test
    void splitsLinesWithoutTrailingEmpty() {
        assertThat(Day34DiffUtil.lines("a\nb\n")).containsExactly("a", "b");
        assertThat(Day34DiffUtil.lines("")).isEmpty();
        assertThat(Day34DiffUtil.lines(null)).isEmpty();
    }
}
