package com.yunovan.aiadvent.day32;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.day21.Day21Document;
import com.yunovan.aiadvent.day21.Day21EmbeddingService;
import com.yunovan.aiadvent.day21.Day21Properties;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day32ReviewIndexTest {

    private Day32ReviewIndex index;

    @BeforeEach
    void setUp() {
        Day32Properties properties = new Day32Properties(".", "main", "HEAD", "docs", "README.md",
                "src/main/java/com/yunovan/aiadvent/day32", 6, 12, 700, 80, 5_000, 14_000, 100, 700, 0.1);
        Day21EmbeddingService embeddings = new Day21EmbeddingService(
                new Day21Properties(null, null, null, null, null, null, null, null, null));
        index = new Day32ReviewIndex(properties, embeddings);
    }

    @Test
    void loadsDocumentationAndCode() {
        Day32ReviewIndex.Snapshot snapshot = index.snapshot();

        assertThat(snapshot.documents()).extracting(Day21Document::fileName)
                .contains("README.md", "architecture.md", "Day32ReviewService.java");
        assertThat(snapshot.docCount()).isPositive();
        assertThat(snapshot.codeCount()).isPositive();
        assertThat(snapshot.chunks()).isNotEmpty();
        assertThat(snapshot.corpusChars()).isPositive();
    }

    @Test
    void searchFindsProjectDocumentation() {
        List<Day21SearchHit> hits = index.search("RAG индекс документов", 6);

        assertThat(hits).isNotEmpty();
        assertThat(hits.stream().map(hit -> hit.chunk().fileName()).distinct().count())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void blankQueryReturnsNothing() {
        assertThat(index.search("   ", 3)).isEmpty();
    }
}
