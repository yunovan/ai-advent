package com.yunovan.aiadvent.day15;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class Day15StateMachineTest {

    @Test
    void canTransitionAllowsOnlyExplicitEdges() {
        assertThat(Day15StateMachine.canTransition(Day15Stage.PLANNING, Day15Stage.PLAN_APPROVED)).isTrue();
        assertThat(Day15StateMachine.canTransition(Day15Stage.PLAN_APPROVED, Day15Stage.EXECUTION)).isTrue();
        assertThat(Day15StateMachine.canTransition(Day15Stage.PLAN_APPROVED, Day15Stage.PLANNING)).isTrue();
        assertThat(Day15StateMachine.canTransition(Day15Stage.EXECUTION, Day15Stage.VALIDATION)).isTrue();
        assertThat(Day15StateMachine.canTransition(Day15Stage.VALIDATION, Day15Stage.DONE)).isTrue();
        assertThat(Day15StateMachine.canTransition(Day15Stage.VALIDATION, Day15Stage.EXECUTION)).isTrue();
    }

    @Test
    void cannotJumpOverStages() {
        assertThat(Day15StateMachine.canTransition(Day15Stage.PLANNING, Day15Stage.EXECUTION)).isFalse();
        assertThat(Day15StateMachine.canTransition(Day15Stage.PLANNING, Day15Stage.VALIDATION)).isFalse();
        assertThat(Day15StateMachine.canTransition(Day15Stage.PLANNING, Day15Stage.DONE)).isFalse();
        assertThat(Day15StateMachine.canTransition(Day15Stage.PLAN_APPROVED, Day15Stage.VALIDATION)).isFalse();
        assertThat(Day15StateMachine.canTransition(Day15Stage.PLAN_APPROVED, Day15Stage.DONE)).isFalse();
        assertThat(Day15StateMachine.canTransition(Day15Stage.EXECUTION, Day15Stage.DONE)).isFalse();
        assertThat(Day15StateMachine.canTransition(Day15Stage.EXECUTION, Day15Stage.PLANNING)).isFalse();
        assertThat(Day15StateMachine.canTransition(Day15Stage.DONE, Day15Stage.PLANNING)).isFalse();
        assertThat(Day15StateMachine.canTransition(Day15Stage.DONE, Day15Stage.DONE)).isFalse();
    }

    @Test
    void transitionReturnsTargetWhenAllowed() {
        assertThat(Day15StateMachine.transition(Day15Stage.PLANNING, Day15Stage.PLAN_APPROVED))
                .isEqualTo(Day15Stage.PLAN_APPROVED);
        assertThat(Day15StateMachine.transition(Day15Stage.VALIDATION, Day15Stage.DONE))
                .isEqualTo(Day15Stage.DONE);
    }

    @Test
    void transitionThrowsOnIllegalJump() {
        assertThatThrownBy(() -> Day15StateMachine.transition(Day15Stage.PLANNING, Day15Stage.EXECUTION))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Недопустимый переход")
                .hasMessageContaining("планирование")
                .hasMessageContaining("выполнение")
                .hasMessageContaining("план утверждён")
                .hasMessageContaining("перепрыгивать");
    }

    @Test
    void transitionThrowsWhenDoneWithoutValidation() {
        assertThatThrownBy(() -> Day15StateMachine.transition(Day15Stage.EXECUTION, Day15Stage.DONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("проверка")
                .hasMessageContaining("завершение");
    }

    @Test
    void allowedTargetsReturnEdges() {
        assertThat(Day15StateMachine.allowedTargets(Day15Stage.PLANNING))
                .containsExactly(Day15Stage.PLAN_APPROVED);
        assertThat(Day15StateMachine.allowedTargets(Day15Stage.PLAN_APPROVED))
                .containsExactly(Day15Stage.PLANNING, Day15Stage.EXECUTION);
        assertThat(Day15StateMachine.allowedTargets(Day15Stage.VALIDATION))
                .containsExactly(Day15Stage.EXECUTION, Day15Stage.DONE);
        assertThat(Day15StateMachine.allowedTargets(Day15Stage.DONE)).isEmpty();
    }

    @Test
    void nextForwardWalksTheCanonicalChain() {
        assertThat(Day15StateMachine.nextForward(Day15Stage.PLANNING)).isEqualTo(Day15Stage.PLAN_APPROVED);
        assertThat(Day15StateMachine.nextForward(Day15Stage.PLAN_APPROVED)).isEqualTo(Day15Stage.EXECUTION);
        assertThat(Day15StateMachine.nextForward(Day15Stage.EXECUTION)).isEqualTo(Day15Stage.VALIDATION);
        assertThat(Day15StateMachine.nextForward(Day15Stage.VALIDATION)).isEqualTo(Day15Stage.DONE);
    }

    @Test
    void nextForwardOnTerminalThrows() {
        assertThatThrownBy(() -> Day15StateMachine.nextForward(Day15Stage.DONE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void doneIsTerminal() {
        assertThat(Day15StateMachine.isTerminal(Day15Stage.DONE)).isTrue();
        assertThat(Day15StateMachine.isTerminal(Day15Stage.PLANNING)).isFalse();
        assertThat(Day15StateMachine.isTerminal(Day15Stage.EXECUTION)).isFalse();
    }

    @Test
    void everyStageHasDefaultAction() {
        for (Day15Stage stage : Day15Stage.values()) {
            assertThat(Day15StateMachine.defaultAction(stage)).isNotBlank();
        }
    }

    @Test
    void displayAllowedJoinsTargets() {
        assertThat(Day15StateMachine.displayAllowed(Day15Stage.PLAN_APPROVED)).isEqualTo("планирование, выполнение");
        assertThat(Day15StateMachine.displayAllowed(Day15Stage.DONE)).contains("нет переходов");
    }

    @Test
    void transitionsHintListsEdges() {
        assertThat(Day15TaskPrompt.transitionsHint(Day15Stage.PLANNING)).contains("PLANNING → PLAN_APPROVED");
        assertThat(Day15TaskPrompt.transitionsHint(Day15Stage.DONE)).contains("переходы недоступны");
    }

    @Test
    void stageFromParsesEnumAndDisplayNames() {
        assertThat(Day15Stage.from("PLAN_APPROVED")).isEqualTo(Day15Stage.PLAN_APPROVED);
        assertThat(Day15Stage.from("план утверждён")).isEqualTo(Day15Stage.PLAN_APPROVED);
        assertThat(Day15Stage.from("выполнение")).isEqualTo(Day15Stage.EXECUTION);
        assertThat(Day15Stage.from("нет такого")).isNull();
        assertThat(Day15Stage.from("  ")).isNull();
    }

    @Test
    void stageDisplayNamesUniqueAndNotEmpty() {
        List<String> displays = java.util.Arrays.stream(Day15Stage.values())
                .map(Day15Stage::display).toList();
        assertThat(displays).doesNotHaveDuplicates();
        assertThat(displays).allMatch(s -> !s.isBlank());
    }
}