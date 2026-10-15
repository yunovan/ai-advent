package com.yunovan.aiadvent.day34;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Day34GoalPlannerTest {

    private final Day34GoalPlanner planner = new Day34GoalPlanner();

    @Test
    void explicitTaskWins() {
        assertThat(planner.classify("найди использования Foo", "adr"))
                .isEqualTo(Day34TaskType.GENERATE_ADR);
        assertThat(planner.classify(null, "invariants")).isEqualTo(Day34TaskType.CHECK_INVARIANTS);
        assertThat(planner.classify("что угодно", null)).isNotNull();
    }

    @Test
    void classifiesGoalsByKeywords() {
        assertThat(planner.classify("Найди все использования Day34Workspace", null))
                .isEqualTo(Day34TaskType.FIND_USAGES);
        assertThat(planner.classify("Сгенерируй README для проекта", null))
                .isEqualTo(Day34TaskType.GENERATE_README);
        assertThat(planner.classify("Собери changelog по последним изменениям", null))
                .isEqualTo(Day34TaskType.GENERATE_CHANGELOG);
        assertThat(planner.classify("Нужен ADR по выбору хранилища", null))
                .isEqualTo(Day34TaskType.GENERATE_ADR);
        assertThat(planner.classify("Проверь инварианты кода", null))
                .isEqualTo(Day34TaskType.CHECK_INVARIANTS);
        assertThat(planner.classify("Обнови документацию по новому API", null))
                .isEqualTo(Day34TaskType.UPDATE_DOC);
    }

    @Test
    void fallsBackToUsageSearch() {
        assertThat(planner.classify("Сделай что-нибудь полезное", null))
                .isEqualTo(Day34TaskType.FIND_USAGES);
    }

    @Test
    void extractsQuotedTarget() {
        assertThat(planner.extractTarget("Найди использования \"WidgetService\" в коде", null))
                .isEqualTo("WidgetService");
    }

    @Test
    void extractsCamelCaseIdentifier() {
        assertThat(planner.extractTarget("Find usages of Day34FileAgent please", null))
                .isEqualTo("Day34FileAgent");
    }

    @Test
    void explicitTargetWins() {
        assertThat(planner.extractTarget("Найди использования Widget", "CustomTarget"))
                .isEqualTo("CustomTarget");
    }

    @Test
    void purposeIsNeverBlank() {
        for (Day34TaskType type : Day34TaskType.values()) {
            assertThat(planner.purpose(type, "тема")).isNotBlank();
            assertThat(planner.keywords(type)).isNotEmpty();
        }
    }
}
