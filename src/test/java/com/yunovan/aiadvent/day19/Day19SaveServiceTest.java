package com.yunovan.aiadvent.day19;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day19SaveServiceTest {

    @TempDir
    Path tempDir;

    private Day19SaveService saveService() {
        return new Day19SaveService(
                new Day19Properties(9092, "/mcp", "ai-advent-pipeline-mcp", "0.1.0", tempDir.toString()));
    }

    private String summaryFor(String query) {
        Day19CatalogService catalog = new Day19CatalogService();
        List<Day19Product> products = catalog.search(query, null, null, "rating");
        return Day19TableBuilder.markdown(query, products);
    }

    private String dataFor(String query) {
        Day19MarketService market = new Day19MarketService(new Day19CatalogService(), saveService());
        return market.productsJson(market.search(query, null, null, "rating"));
    }

    @Test
    void savesMarkdownFileByDefault() throws Exception {
        Day19SavedFile file = saveService().save(summaryFor("ноутбук"), dataFor("ноутбук"),
                null, "laptops");

        assertThat(file.fileName()).isEqualTo("laptops.md");
        assertThat(file.format()).isEqualTo("markdown");
        assertThat(file.bytes()).isPositive();
        assertThat(Files.exists(Path.of(file.path()))).isTrue();
        String content = Files.readString(Path.of(file.path()));
        assertThat(content).startsWith("Сравнение по запросу «ноутбук»");
    }

    @Test
    void savesCsvFileWithProducts() throws Exception {
        Day19SavedFile file = saveService().save(summaryFor("ноутбук"), dataFor("ноутбук"),
                "csv", "laptops");

        assertThat(file.fileName()).isEqualTo("laptops.csv");
        String content = Files.readString(Path.of(file.path()));
        assertThat(content).startsWith("Товар;Продавец;Цена;Рейтинг;Параметры;Ссылка\n");
        assertThat(content).contains("Ноутбук Lenovo IdeaPad 3");
    }

    @Test
    void savesJsonFileWithProducts() throws Exception {
        Day19SavedFile file = saveService().save(summaryFor("ноутбук"), dataFor("ноутбук"),
                "json", "laptops");

        assertThat(file.fileName()).isEqualTo("laptops.json");
        String content = Files.readString(Path.of(file.path()));
        assertThat(content).contains("\"products\"");
        assertThat(content).contains("Ноутбук Lenovo IdeaPad 3");
    }

    @Test
    void defaultFileNameIsComparison() {
        Day19SavedFile file = saveService().save(summaryFor("ноутбук"), null, null, null);

        assertThat(file.fileName()).isEqualTo("comparison.md");
    }

    @Test
    void mdAliasBecomesMarkdownExtension() {
        Day19SavedFile file = saveService().save(summaryFor("ноутбук"), null, "md", "laptops");

        assertThat(file.fileName()).isEqualTo("laptops.md");
    }

    @Test
    void invalidFormatRejected() {
        assertThatThrownBy(() -> saveService().save(summaryFor("ноутбук"), dataFor("ноутбук"),
                "pdf", "laptops"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Неизвестный формат");
    }

    @Test
    void csvWithoutDataRejected() {
        assertThatThrownBy(() -> saveService().save(summaryFor("ноутбук"), null, "csv", "laptops"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("пуст");
    }

    @Test
    void emptySummaryForMarkdownRejected() {
        assertThatThrownBy(() -> saveService().save("", null, "markdown", "laptops"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Нечего сохранять");
    }

    @Test
    void listReturnsSavedFilesAndCreatesNoneBeforeSave() throws Exception {
        Day19SaveService service = saveService();

        assertThat(service.list()).isEmpty();

        service.save(summaryFor("ноутбук"), null, "markdown", "laptops");
        service.save(summaryFor("наушники"), null, "txt", "headphones");

        List<Day19SavedFile> files = service.list();
        assertThat(files).hasSize(2);
        assertThat(files.stream().map(Day19SavedFile::fileName))
                .containsExactly("headphones.txt", "laptops.md");
    }

    @Test
    void fileNameSanitizesUnsafeCharacters() {
        Day19SavedFile file = saveService().save(summaryFor("ноутбук"), null, "markdown",
                "laptops::2026/тест?");

        assertThat(file.fileName()).doesNotContain("::");
        assertThat(file.fileName()).endsWith(".md");
    }

    @Test
    void saveCreatesStoreDirectory() {
        Path nested = tempDir.resolve("a").resolve("b");
        Day19SaveService service = new Day19SaveService(
                new Day19Properties(9092, "/mcp", "ai-advent-pipeline-mcp", "0.1.0", nested.toString()));

        service.save(summaryFor("ноутбук"), null, "markdown", "laptops");

        assertThat(Files.isDirectory(nested)).isTrue();
    }
}