package com.yunovan.aiadvent.day32;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Day32DiffSourceTest {

    private final Day32DiffSource source = new Day32DiffSource(new Day32Properties(null, null, null, null,
            null, null, null, null, null, null, null, null, null, null, null));

    @Test
    void detectsRepositoryInWorkingDirectory() {
        assertThat(source.available()).isTrue();
    }

    @Test
    void branchIsReported() {
        assertThat(source.branch()).isNotBlank();
    }

    @Test
    void diffBetweenSameRefIsEmpty() {
        assertThat(source.diff("HEAD", "HEAD")).isEmpty();
        assertThat(Day32DiffParser.parse(source.diff("HEAD", "HEAD"))).isEmpty();
    }

    @Test
    void changedFilesBetweenSameRefIsEmpty() {
        assertThat(source.changedFiles("HEAD", "HEAD")).isEmpty();
    }
}
