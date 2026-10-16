package com.yunovan.aiadvent.day34;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Day34InvariantRulesTest {

    @Test
    void catalogListsAllRules() {
        assertThat(Day34InvariantRules.describe())
                .extracting(Day34InvariantRules.RuleInfo::name)
                .contains("no-system-out", "no-print-stack-trace", "no-empty-catch", "no-todo",
                        "no-tabs", "line-length", "trailing-whitespace", "final-newline");
    }

    @Test
    void detectsDebugOutputAndTodos() {
        String content = """
                class Demo {
                    void run() {
                        System.out.println("debug");
                        // TODO: убрать
                    }
                }
                """;

        List<Day34Finding> findings = Day34InvariantRules.analyze("src/Demo.java", content);

        assertThat(findings).extracting(Day34Finding::rule)
                .contains("no-system-out", "no-todo");
        assertThat(findings).allMatch(finding -> finding.file().equals("src/Demo.java"));
    }

    @Test
    void detectsEmptyCatchAndStackTrace() {
        String content = """
                class Demo {
                    void run() {
                        try {
                            work();
                        } catch (Exception ex) {
                        }
                        other().printStackTrace();
                    }
                }
                """;

        List<Day34Finding> findings = Day34InvariantRules.analyze("Demo.java", content);

        assertThat(findings).extracting(Day34Finding::rule)
                .contains("no-empty-catch", "no-print-stack-trace");
        Day34Finding emptyCatch = findings.stream()
                .filter(finding -> finding.rule().equals("no-empty-catch")).findFirst().orElseThrow();
        assertThat(emptyCatch.severity()).isEqualTo("high");
    }

    @Test
    void detectsWhitespaceAndLengthIssues() {
        StringBuilder content = new StringBuilder();
        content.append("short line with trailing space \n");
        content.append("\tindented with tab\n");
        content.append("x".repeat(200)).append('\n');

        List<Day34Finding> findings = Day34InvariantRules.analyze("a.txt", content.toString());

        assertThat(findings).extracting(Day34Finding::rule)
                .contains("trailing-whitespace", "no-tabs", "line-length");
    }

    @Test
    void flagsMissingFinalNewlineOnlyForCode() {
        List<Day34Finding> code = Day34InvariantRules.analyze("A.java", "class A {}");
        List<Day34Finding> text = Day34InvariantRules.analyze("a.txt", "text without newline");

        assertThat(code).extracting(Day34Finding::rule).contains("final-newline");
        assertThat(text).extracting(Day34Finding::rule).doesNotContain("final-newline");
    }

    @Test
    void cleanContentHasNoHighSeverityFindings() {
        String content = """
                class Clean {
                    void run() {
                        log.info("ok");
                    }
                }
                """;

        List<Day34Finding> findings = Day34InvariantRules.analyze("Clean.java", content);

        assertThat(findings).noneMatch(finding -> finding.severity().equals("high"));
    }
}
