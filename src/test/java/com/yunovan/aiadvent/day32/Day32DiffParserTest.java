package com.yunovan.aiadvent.day32;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Day32DiffParserTest {

    @Test
    void parsesModifiedFileWithAddedLines() {
        String diff = """
                diff --git a/src/main/java/com/yunovan/aiadvent/day32/Foo.java b/src/main/java/com/yunovan/aiadvent/day32/Foo.java
                index 111..222 100644
                --- a/src/main/java/com/yunovan/aiadvent/day32/Foo.java
                +++ b/src/main/java/com/yunovan/aiadvent/day32/Foo.java
                @@ -1,3 +1,5 @@
                 package com.yunovan.aiadvent.day32;
                 
                +import java.util.List;
                +// TODO fix
                 public class Foo {
                """;

        List<Day32FileDiff> files = Day32DiffParser.parse(diff);

        assertThat(files).hasSize(1);
        Day32FileDiff file = files.getFirst();
        assertThat(file.path()).isEqualTo("src/main/java/com/yunovan/aiadvent/day32/Foo.java");
        assertThat(file.status()).isEqualTo("modified");
        assertThat(file.additions()).isEqualTo(2);
        assertThat(file.deletions()).isZero();
        assertThat(file.hunks()).isEqualTo(1);
        assertThat(file.addedLines()).extracting(Day32Line::number).containsExactly(3, 4);
        assertThat(file.addedLines()).extracting(Day32Line::text)
                .containsExactly("import java.util.List;", "// TODO fix");
        assertThat(file.code()).isTrue();
    }

    @Test
    void parsesAddedFile() {
        String diff = """
                diff --git a/src/main/java/com/yunovan/aiadvent/day32/New.java b/src/main/java/com/yunovan/aiadvent/day32/New.java
                new file mode 100644
                index 000..abc
                --- /dev/null
                +++ b/src/main/java/com/yunovan/aiadvent/day32/New.java
                @@ -0,0 +1,2 @@
                +public class New {
                +}
                """;

        List<Day32FileDiff> files = Day32DiffParser.parse(diff);

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().status()).isEqualTo("added");
        assertThat(files.getFirst().path()).endsWith("New.java");
        assertThat(files.getFirst().additions()).isEqualTo(2);
    }

    @Test
    void parsesDeletedFile() {
        String diff = """
                diff --git a/docs/old.md b/docs/old.md
                deleted file mode 100644
                index abc..000
                --- a/docs/old.md
                +++ /dev/null
                @@ -1,2 +0,0 @@
                -# Old
                -line
                """;

        List<Day32FileDiff> files = Day32DiffParser.parse(diff);

        assertThat(files).hasSize(1);
        Day32FileDiff file = files.getFirst();
        assertThat(file.status()).isEqualTo("deleted");
        assertThat(file.path()).isEqualTo("docs/old.md");
        assertThat(file.additions()).isZero();
        assertThat(file.deletions()).isEqualTo(2);
        assertThat(file.code()).isFalse();
    }

    @Test
    void parsesRenamedFile() {
        String diff = """
                diff --git a/docs/a.md b/docs/b.md
                similarity index 95%
                rename from docs/a.md
                rename to docs/b.md
                """;

        List<Day32FileDiff> files = Day32DiffParser.parse(diff);

        assertThat(files).hasSize(1);
        Day32FileDiff file = files.getFirst();
        assertThat(file.status()).isEqualTo("renamed");
        assertThat(file.path()).isEqualTo("docs/b.md");
        assertThat(file.oldPath()).isEqualTo("docs/a.md");
    }

    @Test
    void blankDiffYieldsNothing() {
        assertThat(Day32DiffParser.parse(null)).isEmpty();
        assertThat(Day32DiffParser.parse("   ")).isEmpty();
    }
}
