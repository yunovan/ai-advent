package com.yunovan.aiadvent.day13;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Day13StateMachineTest {

    @Test
    void nextMovesAlongTheChain() {
        assertThat(Day13StateMachine.next(Day13Stage.PLANNING)).isEqualTo(Day13Stage.EXECUTION);
        assertThat(Day13StateMachine.next(Day13Stage.EXECUTION)).isEqualTo(Day13Stage.VALIDATION);
        assertThat(Day13StateMachine.next(Day13Stage.VALIDATION)).isEqualTo(Day13Stage.DONE);
    }

    @Test
    void nextOnTerminalThrows() {
        assertThatThrownBy(() -> Day13StateMachine.next(Day13Stage.DONE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void canTransitionOnlyForward() {
        assertThat(Day13StateMachine.canTransition(Day13Stage.PLANNING, Day13Stage.EXECUTION)).isTrue();
        assertThat(Day13StateMachine.canTransition(Day13Stage.EXECUTION, Day13Stage.VALIDATION)).isTrue();
        assertThat(Day13StateMachine.canTransition(Day13Stage.VALIDATION, Day13Stage.DONE)).isTrue();
        assertThat(Day13StateMachine.canTransition(Day13Stage.EXECUTION, Day13Stage.PLANNING)).isFalse();
        assertThat(Day13StateMachine.canTransition(Day13Stage.PLANNING, Day13Stage.DONE)).isFalse();
        assertThat(Day13StateMachine.canTransition(Day13Stage.DONE, Day13Stage.PLANNING)).isFalse();
    }

    @Test
    void doneIsTerminal() {
        assertThat(Day13StateMachine.isTerminal(Day13Stage.DONE)).isTrue();
        assertThat(Day13StateMachine.isTerminal(Day13Stage.PLANNING)).isFalse();
        assertThat(Day13StateMachine.isTerminal(Day13Stage.EXECUTION)).isFalse();
        assertThat(Day13StateMachine.isTerminal(Day13Stage.VALIDATION)).isFalse();
    }

    @Test
    void everyStageHasDefaultExpectedAction() {
        for (Day13Stage stage : Day13Stage.values()) {
            assertThat(Day13StateMachine.defaultExpectedAction(stage)).isNotBlank();
        }
    }
}