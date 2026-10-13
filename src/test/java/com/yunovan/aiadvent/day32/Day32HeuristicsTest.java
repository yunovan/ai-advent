package com.yunovan.aiadvent.day32;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Day32HeuristicsTest {

    private static Day32FileDiff codeFile(String path, int additions, Day32Line... addedLines) {
        return new Day32FileDiff(path, "", "modified", additions, 0, 1, List.of(addedLines));
    }

    @Test
    void flagsHardcodedSecretAsBug() {
        Day32FileDiff file = codeFile("src/main/java/Foo.java", 1,
                new Day32Line(12, "String apiKey = \"sk-abcdef123456\";"));

        Day32Heuristics.Result result = Day32Heuristics.analyze(List.of(file));

        assertThat(result.bugs()).anyMatch(finding -> finding.severity().equals("high")
                && finding.message().contains("секрет"));
    }

    @Test
    void flagsEmptyBroadCatch() {
        Day32FileDiff file = codeFile("src/main/java/Foo.java", 2,
                new Day32Line(20, "} catch (Exception ex) {"),
                new Day32Line(21, "}"));

        Day32Heuristics.Result result = Day32Heuristics.analyze(List.of(file));

        assertThat(result.bugs()).anyMatch(finding -> finding.message().contains("пустой catch"));
        assertThat(result.architecture())
                .anyMatch(finding -> finding.message().contains("Exception/Throwable"));
    }

    @Test
    void flagsDebugOutputAndTodo() {
        Day32FileDiff file = codeFile("src/main/java/Foo.java", 2,
                new Day32Line(5, "System.out.println(\"debug\");"),
                new Day32Line(6, "// TODO переписать"));

        Day32Heuristics.Result result = Day32Heuristics.analyze(List.of(file));

        assertThat(result.bugs()).anyMatch(finding -> finding.message().contains("отладочный вывод"));
        assertThat(result.recommendations()).anyMatch(finding -> finding.message().contains("TODO"));
    }

    @Test
    void recommendsTestsWhenOnlyCodeChanged() {
        Day32FileDiff file = codeFile("src/main/java/Foo.java", 3,
                new Day32Line(1, "int x = 1;"));

        Day32Heuristics.Result result = Day32Heuristics.analyze(List.of(file));

        assertThat(result.recommendations())
                .anyMatch(finding -> finding.message().contains("нет ни одного тестового файла"));
    }

    @Test
    void noTestRecommendationWhenTestsPresent() {
        Day32FileDiff code = codeFile("src/main/java/Foo.java", 1,
                new Day32Line(1, "int x = 1;"));
        Day32FileDiff test = codeFile("src/test/java/FooTest.java", 1,
                new Day32Line(1, "assertThat(true).isTrue();"));

        Day32Heuristics.Result result = Day32Heuristics.analyze(List.of(code, test));

        assertThat(result.recommendations())
                .noneMatch(finding -> finding.message().contains("нет ни одного тестового файла"));
    }

    @Test
    void flagsLargeFileAsArchitecture() {
        Day32FileDiff file = codeFile("src/main/java/Foo.java", 250,
                new Day32Line(1, "int x = 1;"));

        Day32Heuristics.Result result = Day32Heuristics.analyze(List.of(file));

        assertThat(result.architecture()).anyMatch(finding -> finding.message().contains("крупное изменение"));
    }

    @Test
    void flagsDeletedCodeFile() {
        Day32FileDiff file = new Day32FileDiff("src/main/java/Gone.java", "", "deleted", 0, 40, 0, List.of());

        Day32Heuristics.Result result = Day32Heuristics.analyze(List.of(file));

        assertThat(result.architecture()).anyMatch(finding -> finding.message().contains("удалён"));
    }
}
