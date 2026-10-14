package com.yunovan.aiadvent.day32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21Document;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day26.Day26Answer;
import com.yunovan.aiadvent.day26.Day26ChatOptions;
import com.yunovan.aiadvent.day26.Day26InstalledModel;
import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day32ReviewServiceTest {

    private static final String SIMPLE_DIFF = """
            diff --git a/src/main/java/Foo.java b/src/main/java/Foo.java
            --- a/src/main/java/Foo.java
            +++ b/src/main/java/Foo.java
            @@ -1,0 +1,1 @@
            +int x = 1;
            """;

    private static final String RISKY_DIFF = """
            diff --git a/src/main/java/Foo.java b/src/main/java/Foo.java
            --- a/src/main/java/Foo.java
            +++ b/src/main/java/Foo.java
            @@ -1,0 +1,3 @@
            +String apiKey = "sk-abcdef123456";
            +System.out.println("debug");
            +// TODO переписать
            """;

    private static final String LLM_REPLY = """
            ## Потенциальные баги
            - src/main/java/Foo.java:12 высокий риск NPE при null
            ## Архитектурные проблемы
            - нет
            ## Рекомендации
            - вынесите получение значения в отдельный метод
            """;

    private final Day32Properties properties = new Day32Properties(null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);
    private final Day32ReviewIndex index = mock(Day32ReviewIndex.class);
    private final Day32DiffSource git = mock(Day32DiffSource.class);
    private final Day26LocalLlmClient llm = mock(Day26LocalLlmClient.class);
    private final Day26Properties llmProperties = new Day26Properties(null, null, null, null, null, null);

    private Day32ReviewService service;

    @BeforeEach
    void setUp() {
        service = new Day32ReviewService(properties, index, git, llm, llmProperties);
        when(index.snapshot()).thenReturn(new Day32ReviewIndex.Snapshot(List.of(), List.of(), List.of(), 0, 0, 0));
        when(index.search(anyString(), anyInt())).thenReturn(List.of());
    }

    private static Day21SearchHit hit() {
        Day21Chunk chunk = new Day21Chunk("fixed", "docs/architecture.md", "architecture.md",
                "Архитектура", "Слои RAG", "docs/architecture.md#fixed#0001", 0, 40,
                "Проект использует локальные эмбеддинги и индекс.");
        return new Day21SearchHit(chunk, 0.8, "Проект использует локальные эмбеддинги");
    }

    @Test
    void healthReportsIndexAndModel() {
        Day21Chunk chunk = new Day21Chunk("fixed", "docs/architecture.md", "architecture.md",
                "Архитектура", "Слои RAG", "docs/architecture.md#fixed#0001", 0, 40, "текст");
        when(index.snapshot()).thenReturn(new Day32ReviewIndex.Snapshot(
                List.of(new Day21Document("docs/architecture.md", "architecture.md", "Архитектура",
                        "markdown", "text")),
                List.of(chunk), List.of(), 100, 1, 4));
        when(git.available()).thenReturn(true);
        when(git.branch()).thenReturn("main");
        when(git.repositoryDir()).thenReturn(Path.of("."));
        when(llm.version()).thenReturn("0.35.1");
        when(llm.models()).thenReturn(List.of(new Day26InstalledModel("qwen2.5:3b", 1L, "3.1B", "Q4_K_M")));

        Day32HealthResponse health = service.health();

        assertThat(health.status()).isEqualTo("ok");
        assertThat(health.branch()).isEqualTo("main");
        assertThat(health.documents()).isEqualTo(1);
        assertThat(health.codeFiles()).isEqualTo(4);
        assertThat(health.model().available()).isTrue();
        assertThat(health.model().installed()).isTrue();
    }

    @Test
    void reviewBuildsFindingsFromModelAndHeuristics() {
        when(index.search(anyString(), anyInt())).thenReturn(List.of(hit()));
        when(llm.chat(anyList(), any(Day26ChatOptions.class))).thenReturn(new Day26Answer(
                "prompt", LLM_REPLY, "qwen2.5:3b", "http://localhost:11434", 800L, 150, 60, 8.0));

        Day32ReviewResponse response = service.review(new Day32ReviewRequest("main", "HEAD",
                RISKY_DIFF, "Добавил Foo", "небольшой PR"));

        assertThat(response.fallback()).isFalse();
        assertThat(response.review()).contains("NPE");
        assertThat(response.bugs()).anyMatch(finding -> finding.file().equals("src/main/java/Foo.java")
                && finding.category().equals("bug"));
        assertThat(response.bugs()).anyMatch(finding -> finding.severity().equals("high")
                && finding.message().contains("секрет"));
        assertThat(response.recommendations()).anyMatch(finding -> finding.message().contains("TODO"));
        assertThat(response.retrieved()).isEqualTo(1);
        assertThat(response.sources()).contains("architecture.md");
        assertThat(response.summary()).contains("моделью");
    }

    @Test
    void reviewFallsBackToHeuristicsWhenModelUnavailable() {
        when(llm.chat(anyList(), any(Day26ChatOptions.class)))
                .thenThrow(new Day26LlmException("Ollama недоступна"));

        Day32ReviewResponse response = service.review(new Day32ReviewRequest("main", "HEAD",
                RISKY_DIFF, "Добавил Foo", null));

        assertThat(response.fallback()).isTrue();
        assertThat(response.note()).contains("Ollama недоступна");
        assertThat(response.review()).contains(Day32ReviewService.SECTION_BUGS);
        assertThat(response.bugs()).isNotEmpty();
    }

    @Test
    void reviewWithoutProvidedDiffAsksGit() {
        when(git.diff("main", "HEAD")).thenReturn(SIMPLE_DIFF);
        when(llm.chat(anyList(), any(Day26ChatOptions.class))).thenReturn(new Day26Answer(
                "prompt", LLM_REPLY, "qwen2.5:3b", "http://localhost:11434", 10L, 1, 1, 1.0));

        Day32ReviewResponse response = service.review(new Day32ReviewRequest("main", "HEAD", null, null, null));

        verify(git).diff("main", "HEAD");
        assertThat(response.filesChanged()).isEqualTo(1);
    }

    @Test
    void reviewWithEmptyDiffReturnsNotice() {
        when(git.diff("main", "HEAD")).thenReturn("");

        Day32ReviewResponse response = service.review(new Day32ReviewRequest("main", "HEAD", null, null, null));

        assertThat(response.filesChanged()).isZero();
        assertThat(response.summary()).contains("не найдено");
    }

    @Test
    void changedFilesDelegatesToGit() {
        when(git.changedFiles("main", "HEAD"))
                .thenReturn(List.of(new Day32ChangedFile("src/main/java/Foo.java", "modified", 3, 1)));

        List<Day32ChangedFile> files = service.changedFiles("main", "HEAD");

        assertThat(files).hasSize(1);
        assertThat(files.getFirst().path()).isEqualTo("src/main/java/Foo.java");
    }

    @Test
    void parseReviewSplitsSections() {
        Day32ReviewService.Sections sections = Day32ReviewService.parseReview(LLM_REPLY);

        assertThat(sections.bugs()).hasSize(1);
        assertThat(sections.bugs().getFirst().file()).isEqualTo("src/main/java/Foo.java");
        assertThat(sections.bugs().getFirst().line()).isEqualTo(12);
        assertThat(sections.architecture()).isEmpty();
        assertThat(sections.recommendations()).hasSize(1);
    }
}
