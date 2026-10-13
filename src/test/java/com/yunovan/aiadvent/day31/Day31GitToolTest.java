package com.yunovan.aiadvent.day31;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Day31GitToolTest {

    private final Day31GitTool git = new Day31GitTool(props());

    private static Day31Properties props() {
        return new Day31Properties(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    @Test
    void detectsRepositoryInWorkingDirectory() {
        assertThat(git.available()).isTrue();
    }

    @Test
    void infoReportsBranchAndCommit() {
        Day31GitInfo info = git.info();

        assertThat(info.available()).isTrue();
        assertThat(info.branch()).isNotBlank();
        assertThat(info.commit()).isNotBlank();
        assertThat(info.reason()).isEmpty();
    }

    @Test
    void statusDescribesWorkingTree() {
        String status = git.status();

        assertThat(status).contains("Ветка:");
        assertThat(status).contains("Изменённых файлов:");
    }

    @Test
    void filesListsTrackedSources() {
        String files = git.files("src/main/java/com/yunovan/aiadvent/day21", 200);

        assertThat(files).contains("Day21EmbeddingService.java");
        assertThat(files).contains("Day21IndexFacade.java");
    }

    @Test
    void diffAndLogReturnText() {
        assertThat(git.diff(false, null)).isNotBlank();
        assertThat(git.log(5)).contains("коммиты");
    }
}
