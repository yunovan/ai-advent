package com.yunovan.aiadvent.day21;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Day21FixedChunkerTest {

    private final Day21Properties properties = new Day21Properties(
            0, "/mcp", "test", "0.0.1", "build/day21chunker", "build/day21chunker-corpus", 200, 30, 64);
    private final Day21FixedChunker chunker = new Day21FixedChunker(properties);

    @Test
    void emptyDocumentYieldsNoChunks() {
        Day21Document document = new Day21Document(
                "articles/empty.md", "empty.md", "empty.md", "markdown", "");

        assertThat(chunker.chunk(document)).isEmpty();
    }

    @Test
    void shortDocumentBecomesOneWholeChunk() {
        Day21Document document = new Day21Document(
                "articles/short.md", "short.md", "short.md", "markdown",
                "Один короткий документ целиком.");

        List<Day21Chunk> chunks = chunker.chunk(document);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).charCount()).isEqualTo(document.text().length());
        assertThat(chunks.get(0).chunkId()).isEqualTo("articles/short.md#fixed#0001");
    }

    @Test
    void chunksStayWithinSizeWithTolerance() {
        String body = paragraphs("Параграф о разбиении документов на чанки и эмбеддинги. ", 120);
        Day21Document document = new Day21Document(
                "articles/large.md", "large.md", "large.md", "markdown", body);

        List<Day21Chunk> chunks = chunker.chunk(document);

        assertThat(chunks).isNotEmpty();
        for (Day21Chunk chunk : chunks) {
            assertThat(chunk.charCount()).isLessThanOrEqualTo(properties.chunkSize() + 70);
            assertThat(chunk.text()).as("чанк %s не должен быть пустым", chunk.chunkId()).isNotBlank();
        }
    }

    @Test
    void sectionsFollowNearestPrecedingMarkdownHeading() {
        String text = "# Раздел первый\n"
                + "Текст первого раздела про планирование и задачи.\n\n"
                + "# Раздел второй\n"
                + "Текст второго раздела про документы, индексы и поиск по ним.\n\n"
                + paragraphs("Повторение про память агента и долговременное хранение. ", 60);
        Day21Document document = new Day21Document(
                "articles/sections.md", "sections.md", "Раздел первый", "markdown", text);

        List<Day21Chunk> chunks = chunker.chunk(document);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.stream().map(Day21Chunk::section))
                .contains("Раздел первый")
                .contains("Раздел второй");
        for (Day21Chunk chunk : chunks) {
            assertThat(chunk.source()).isEqualTo("articles/sections.md");
            assertThat(chunk.title()).isEqualTo("Раздел первый");
            assertThat(chunk.chunkId()).contains("#fixed#");
        }
    }

    @Test
    void documentEndIsCoveredByLastChunk() {
        String body = paragraphs("Текст с несколькими предложениями для нарезки. ", 40).trim();
        Day21Document document = new Day21Document(
                "articles/cover.md", "cover.md", "cover.md", "markdown", body);

        List<Day21Chunk> chunks = chunker.chunk(document);

        Day21Chunk last = chunks.get(chunks.size() - 1);
        assertThat(last.startOffset() + last.charCount()).isEqualTo(document.text().length());
    }

    private static String paragraphs(String sentence, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(sentence);
        }
        return builder.toString();
    }
}