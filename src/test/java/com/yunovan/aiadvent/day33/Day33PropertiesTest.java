package com.yunovan.aiadvent.day33;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Day33PropertiesTest {

    @Test
    void defaultsWhenValuesMissing() {
        Day33Properties properties = new Day33Properties(null, null, null, null, null, null,
                null, null, null, null, null, null);

        assertThat(properties.serverPort()).isEqualTo(Day33Properties.DEFAULT_SERVER_PORT);
        assertThat(properties.path()).isEqualTo("/mcp");
        assertThat(properties.name()).isEqualTo("ai-advent-crm-mcp");
        assertThat(properties.kbDir()).isEqualTo("data/day33-kb");
        assertThat(properties.crmDir()).isEqualTo("data/day33-crm");
        assertThat(properties.topK()).isEqualTo(Day33Properties.DEFAULT_TOP_K);
        assertThat(properties.temperature()).isEqualTo(Day33Properties.DEFAULT_TEMPERATURE);
    }

    @Test
    void testModeAllowsZeroPort() {
        Day33Properties properties = new Day33Properties(0, "/mcp", null, null, "kb", "crm",
                null, null, null, null, null, null);

        assertThat(properties.serverPort()).isZero();
    }

    @Test
    void clampsOutOfRangeValues() {
        Day33Properties properties = new Day33Properties(70_000, "  ", null, null, " ", "",
                -5, 1_000, 10, 5_000, 9_999, 5.0);

        assertThat(properties.serverPort()).isEqualTo(65_535);
        assertThat(properties.path()).isEqualTo("/mcp");
        assertThat(properties.kbDir()).isEqualTo("data/day33-kb");
        assertThat(properties.crmDir()).isEqualTo("data/day33-crm");
        assertThat(properties.topK()).isEqualTo(Day33Properties.DEFAULT_TOP_K);
        assertThat(properties.candidateK()).isEqualTo(100);
        assertThat(properties.chunkSize()).isEqualTo(Day33Properties.DEFAULT_CHUNK_SIZE);
        assertThat(properties.chunkOverlap()).isEqualTo(2_000);
        assertThat(properties.answerMaxTokens()).isEqualTo(4_096);
        assertThat(properties.temperature()).isEqualTo(2.0);
    }

    @Test
    void trimsTextValues() {
        Day33Properties properties = new Day33Properties(9096, "/crm", " name ", " 2.0 ",
                " data/kb ", " data/crm ", null, null, null, null, null, null);

        assertThat(properties.path()).isEqualTo("/crm");
        assertThat(properties.name()).isEqualTo("name");
        assertThat(properties.version()).isEqualTo("2.0");
        assertThat(properties.kbDir()).isEqualTo("data/kb");
        assertThat(properties.crmDir()).isEqualTo("data/crm");
    }
}
