package com.yunovan.aiadvent.day33;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.day21.Day21Document;
import com.yunovan.aiadvent.day21.Day21EmbeddingService;
import com.yunovan.aiadvent.day21.Day21Properties;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day33SupportIndexTest {

    private Day33SupportIndex index;

    @BeforeEach
    void setUp() {
        Day33Properties properties = new Day33Properties(null, null, null, null,
                "data/day33-none", "build/day33-none", null, null, 700, 80, null, null);
        Day21EmbeddingService embeddings = new Day21EmbeddingService(
                new Day21Properties(null, null, null, null, null, null, null, null, null));
        index = new Day33SupportIndex(properties, embeddings);
    }

    @Test
    void loadsBundledKnowledgeBase() {
        Day33SupportIndex.Snapshot snapshot = index.snapshot();

        assertThat(snapshot.documents()).extracting(Day21Document::fileName)
                .contains("authorization.md", "faq.md", "billing.md", "integrations.md");
        assertThat(snapshot.chunks()).isNotEmpty();
        assertThat(snapshot.corpusChars()).isPositive();
    }

    @Test
    void searchFindsAuthorizationGuide() {
        List<Day21SearchHit> hits = index.search("почему не работает авторизация", 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits).extracting(hit -> hit.chunk().fileName())
                .contains("authorization.md");
        assertThat(hits.getFirst().score()).isPositive();
    }

    @Test
    void searchSpreadsChunksAcrossFiles() {
        List<Day21SearchHit> hits = index.search("авторизация оплата отчёты интеграции", 5);

        assertThat(hits).isNotEmpty();
        assertThat(hits.stream().map(hit -> hit.chunk().fileName()).distinct().count())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void blankQueryReturnsNothing() {
        assertThat(index.search("   ", 3)).isEmpty();
        assertThat(index.search(null, 3)).isEmpty();
    }

    @Test
    void reindexRebuildsSnapshot() {
        Day33SupportIndex.Snapshot first = index.snapshot();
        index.reindex();
        Day33SupportIndex.Snapshot second = index.snapshot();

        assertThat(second.documents()).hasSameSizeAs(first.documents());
    }
}
