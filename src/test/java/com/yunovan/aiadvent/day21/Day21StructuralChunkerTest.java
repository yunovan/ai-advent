package com.yunovan.aiadvent.day21;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Day21StructuralChunkerTest {

    private final Day21Properties properties = new Day21Properties(
            0, "/mcp", "test", "0.0.1", "build/day21chunker", "build/day21chunker-corpus", 200, 30, 64);
    private final Day21StructuralChunker chunker = new Day21StructuralChunker(properties);

    @Test
    void markdownDocumentHonorsHeadingsAndParagraphs() {
        String text = "# Раздел подготовки\n"
                + "Параграф про подготовку документов к индексации: здесь описано, как читается корпус, "
                + "как вырезается текст из markdown, кода и pdf, и какие метаданные сохраняются у документа.\n\n"
                + "Второй короткий параграф того же раздела.\n\n"
                + "# Раздел поиска\n"
                + "Параграф про семантический поиск по эмбеддингам: запрос превращается в вектор, "
                + "сравнивается с векторами чанков по косинусной близости, и возвращается топ результатов "
                + "с источниками и секциями. Это финал всего пайплайна индексации.\n";
        Day21Document document = new Day21Document(
                "articles/struct.md", "struct.md", "Раздел подготовки", "markdown", text);

        List<Day21Chunk> chunks = chunker.chunk(document);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.stream().map(Day21Chunk::section))
                .contains("Раздел подготовки")
                .contains("Раздел поиска");
        for (Day21Chunk chunk : chunks) {
            assertThat(chunk.chunkId()).contains("#structural#");
            assertThat(chunk.text()).isNotBlank();
        }
    }

    @Test
    void codeDocumentUsesClassNamesAsSections() {
        String code =
                "package com.yunovan.sample;\n\npublic class ChunkerSample {\n"
                        + "    // method one: возвращает первый кусок текста документа\n"
                        + "    private static String chunkOne(String source, int offset) {\n"
                        + "        return \"первый кусок длиной заметно больше ста символов чтобы точно не влезть\";\n"
                        + "    }\n\n"
                        + "    // method two: возвращает второй кусок документа\n"
                        + "    private static String chunkTwo(String source, int offset) {\n"
                        + "        return \"второй кусок тоже достаточно длинный для отдельного чанка\";\n"
                        + "    }\n}\n\n"
                        + "public interface SearchApi {\n"
                        + "    void search(String query, int topK);\n"
                        + "  \n"
                        + "    default String describe() {\n"
                        + "        return \"семантический поиск по косинусной близости эмбеддингов\";\n"
                        + "    }\n"
                        + "}\n";
        Day21Document document = new Day21Document(
                "code/ChunkerSample.java", "ChunkerSample.java", "ChunkerSample", "code", code);

        List<Day21Chunk> chunks = chunker.chunk(document);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.stream().map(Day21Chunk::section))
                .contains("ChunkerSample")
                .contains("SearchApi");
    }

    @Test
    void longParagraphIsSplitIntoBoundedPieces() {
        StringBuilder text = new StringBuilder("Очень длинный параграф. ");
        while (text.length() < 900) {
            text.append("Предложение про чанки, эмбеддинги и метаданные индекса. ");
        }
        Day21Document document = new Day21Document(
                "articles/long.md", "long.md", "long.md", "plain", text.toString());

        List<Day21Chunk> chunks = chunker.chunk(document);

        assertThat(chunks.size()).isGreaterThan(1);
        for (Day21Chunk chunk : chunks) {
            assertThat(chunk.charCount()).isLessThanOrEqualTo(properties.chunkSize() + 70);
        }
    }

    @Test
    void emptyMarkdownStillProducesNoChunks() {
        Day21Document document = new Day21Document(
                "articles/empty.md", "empty.md", "empty.md", "markdown", "");

        assertThat(chunker.chunk(document)).isEmpty();
    }
}