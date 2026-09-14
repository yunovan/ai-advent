package com.yunovan.aiadvent.day11;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Day11MemoryRulesTest {

    @Test
    void profileMarkersSuggestLongTerm() {
        assertThat(Day11MemoryRules.suggestLayer("имя пользователя")).isEqualTo(Day11MemoryLayer.LONG_TERM);
        assertThat(Day11MemoryRules.suggestLayer("предпочтение") ).isEqualTo(Day11MemoryLayer.LONG_TERM);
        assertThat(Day11MemoryRules.suggestLayer("любимый стек")).isEqualTo(Day11MemoryLayer.LONG_TERM);
        assertThat(Day11MemoryRules.suggestLayer("город")).isEqualTo(Day11MemoryLayer.LONG_TERM);
    }

    @Test
    void taskMarkersSuggestWorking() {
        assertThat(Day11MemoryRules.suggestLayer("цель")).isEqualTo(Day11MemoryLayer.WORKING);
        assertThat(Day11MemoryRules.suggestLayer("требование") ).isEqualTo(Day11MemoryLayer.WORKING);
        assertThat(Day11MemoryRules.suggestLayer("бюджет")).isEqualTo(Day11MemoryLayer.WORKING);
        assertThat(Day11MemoryRules.suggestLayer("срок сдачи")).isEqualTo(Day11MemoryLayer.WORKING);
    }

    @Test
    void neutralKeysStayInShortTerm() {
        assertThat(Day11MemoryRules.suggestLayer("погода")).isEqualTo(Day11MemoryLayer.SHORT_TERM);
        assertThat(Day11MemoryRules.suggestLayer(null)).isEqualTo(Day11MemoryLayer.SHORT_TERM);
    }

    @Test
    void profileWinsOverTask() {
        assertThat(Day11MemoryRules.suggestLayer("имя для задачи")).isEqualTo(Day11MemoryLayer.LONG_TERM);
    }
}