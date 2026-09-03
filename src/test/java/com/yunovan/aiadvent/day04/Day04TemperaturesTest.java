package com.yunovan.aiadvent.day04;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Day04TemperaturesTest {

    @Test
    void bestForMapsAccuracyCreativityAndUseCases() {
        assertThat(Day04Temperatures.bestFor(0.0)).contains("Факты");
        assertThat(Day04Temperatures.bestFor(0.7)).contains("диалог");
        assertThat(Day04Temperatures.bestFor(1.2)).contains("Идеи");
    }
}
