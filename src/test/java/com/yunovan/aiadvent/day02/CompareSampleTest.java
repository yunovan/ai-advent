package com.yunovan.aiadvent.day02;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompareSampleTest {

    @Test
    void countsWordsAndCharacters() {
        CompareSample sample = CompareSample.from("один два  три", "stop");

        assertThat(sample.words()).isEqualTo(3);
        assertThat(sample.characters()).isEqualTo(13);
        assertThat(sample.finishReason()).isEqualTo("stop");
    }

    @Test
    void emptyTextHasZeroWords() {
        assertThat(CompareSample.from("   ", null).words()).isZero();
    }
}
