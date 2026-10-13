package com.yunovan.aiadvent.day31;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.day21.Day21Document;
import com.yunovan.aiadvent.day21.Day21EmbeddingService;
import com.yunovan.aiadvent.day21.Day21Properties;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day31ProjectIndexTest {

    private Day31ProjectIndex index;

    @BeforeEach
    void setUp() {
        Day31Properties properties = new Day31Properties(null, null, null, null, null, null, null,
                null, null, 700, 80, null, null, null, null, null);
        Day21EmbeddingService embeddings = new Day21EmbeddingService(
                new Day21Properties(null, null, null, null, null, null, null, null, null));
        index = new Day31ProjectIndex(properties, embeddings);
    }

    @Test
    void loadsReadmeAndDocs() {
        Day31ProjectIndex.Snapshot snapshot = index.snapshot();

        assertThat(snapshot.documents()).extracting(Day21Document::fileName)
                .contains("README.md", "architecture.md", "mcp.md");
        assertThat(snapshot.chunks()).isNotEmpty();
        assertThat(snapshot.corpusChars()).isPositive();
    }

    @Test
    void searchFindsRagSectionInDocs() {
        List<Day21SearchHit> hits = index.search("Слои RAG", 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits).extracting(hit -> hit.chunk().fileName()).contains("architecture.md");
        assertThat(hits.getFirst().score()).isPositive();
    }

    @Test
    void searchSpreadsChunksAcrossFiles() {
        List<Day21SearchHit> hits = index.search("RAG индекс документов", 5);

        assertThat(hits).hasSize(5);
        assertThat(hits.stream().map(hit -> hit.chunk().fileName()).distinct().count())
                .isGreaterThanOrEqualTo(2);
        assertThat(hits).extracting(hit -> hit.chunk().fileName())
                .anyMatch(name -> name.endsWith(".md") && !name.equals("README.md"));
    }

    @Test
    void blankQueryReturnsNothing() {
        assertThat(index.search("   ", 3)).isEmpty();
    }
}
