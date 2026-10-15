package com.yunovan.aiadvent.day34;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Day34PropertiesTest {

    private static Day34Properties defaults() {
        return new Day34Properties(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null);
    }

    @Test
    void appliesDefaultsWhenBlank() {
        Day34Properties properties = defaults();

        assertThat(properties.serverPort()).isEqualTo(Day34Properties.DEFAULT_SERVER_PORT);
        assertThat(properties.path()).isEqualTo("/mcp");
        assertThat(properties.name()).isEqualTo("ai-advent-file-mcp");
        assertThat(properties.workspaceDir()).isEqualTo(".");
        assertThat(properties.codeDir()).isEqualTo("src/main/java");
        assertThat(properties.outputDir()).isEqualTo("build/day34-output");
        assertThat(properties.topK()).isEqualTo(Day34Properties.DEFAULT_TOP_K);
        assertThat(properties.answerMaxTokens()).isEqualTo(700);
        assertThat(properties.temperature()).isEqualTo(0.2);
    }

    @Test
    void clampsOutOfRangeValues() {
        Day34Properties properties = new Day34Properties(70000, "  /files  ", null, null, null,
                null, null, null, null, 0, 1000, 10, -1, 100_000, 10, 1, 99_999, 0, 5.0);

        assertThat(properties.serverPort()).isEqualTo(65_535);
        assertThat(properties.path()).isEqualTo("/files");
        assertThat(properties.topK()).isEqualTo(Day34Properties.DEFAULT_TOP_K);
        assertThat(properties.candidateK()).isEqualTo(100);
        assertThat(properties.chunkSize()).isEqualTo(Day34Properties.DEFAULT_CHUNK_SIZE);
        assertThat(properties.chunkOverlap()).isEqualTo(Day34Properties.DEFAULT_CHUNK_OVERLAP);
        assertThat(properties.maxFiles()).isEqualTo(10_000);
        assertThat(properties.maxFileBytes()).isEqualTo(Day34Properties.DEFAULT_MAX_FILE_BYTES);
        assertThat(properties.maxReadLines()).isEqualTo(Day34Properties.DEFAULT_MAX_READ_LINES);
        assertThat(properties.searchMaxResults()).isEqualTo(5_000);
        assertThat(properties.answerMaxTokens()).isEqualTo(700);
        assertThat(properties.temperature()).isEqualTo(2.0);
    }

    @Test
    void recognizesAllowedExtensions() {
        assertThat(Day34Properties.isAllowed("Foo.java")).isTrue();
        assertThat(Day34Properties.isAllowed("README.md")).isTrue();
        assertThat(Day34Properties.isAllowed("app.yml")).isTrue();
        assertThat(Day34Properties.isAllowed("Build.Gradle")).isTrue();
        assertThat(Day34Properties.isAllowed("script.exe")).isFalse();
        assertThat(Day34Properties.isAllowed("Makefile")).isFalse();
        assertThat(Day34Properties.isAllowed("trailing.")).isFalse();
    }
}
