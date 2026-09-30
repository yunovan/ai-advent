package com.yunovan.aiadvent.day21;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class Day21EmbeddingServiceTest {

    private final Day21Properties properties = new Day21Properties(
            null, null, null, null, null, null, null, null, null);
    private final Day21EmbeddingService service = new Day21EmbeddingService(properties);

    @Test
    void embedsToConfiguredDimensionality() {
        Day21Embedding embedding = service.embed("эмбеддинг чанка текста");

        assertThat(embedding.values()).hasSize(Day21Properties.DEFAULT_DIMENSIONS);
    }

    @Test
    void embeddingIsDeterministic() {
        Day21Embedding first = service.embed("один и тот же текст документа");
        Day21Embedding second = service.embed("один и тот же текст документа");

        assertThat(first.values()).isEqualTo(second.values());
    }

    @Test
    void cosineOfIdenticalTextsIsCloseToOne() {
        Day21Embedding first = service.embed("косинусная близость эмбеддингов");

        assertThat(Day21EmbeddingService.cosine(first, first)).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void relatedTextScoresHigherThanUnrelated() {
        Day21Embedding query = service.embed("индекс документов и поиск по эмбеддингам");
        Day21Embedding related = service.embed("документы индексируются для семантического поиска");
        Day21Embedding unrelated = service.embed("рецепт борща из свёклы с капустой");

        assertThat(Day21EmbeddingService.cosine(query, related))
                .isGreaterThan(Day21EmbeddingService.cosine(query, unrelated));
    }

    @Test
    void emptyTextProducesZeroCosineWithAnyVector() {
        Day21Embedding empty = service.embed("");
        Day21Embedding other = service.embed("что-то значимое");

        assertThat(Day21EmbeddingService.cosine(empty, other)).isEqualTo(0.0);
    }
}