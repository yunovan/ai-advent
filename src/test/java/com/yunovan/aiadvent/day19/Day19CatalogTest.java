package com.yunovan.aiadvent.day19;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Day19CatalogTest {

    private final Day19CatalogService catalog = new Day19CatalogService();

    @Test
    void searchFindsAllLaptops() {
        List<Day19Product> products = catalog.search("ноутбук", null, null, null);

        assertThat(products).hasSize(5);
        assertThat(products).extracting(Day19Product::id)
                .containsExactly("p01", "p02", "p03", "p04", "p05");
    }

    @Test
    void searchByEmptyQueryReturnsAllThirteenProducts() {
        List<Day19Product> products = catalog.search("", null, null, null);

        assertThat(products).hasSize(13);
    }

    @Test
    void searchByCategoryFiltersCatalog() {
        List<Day19Product> products = catalog.search("", "смартфоны", null, null);

        assertThat(products).hasSize(4);
        assertThat(products).extracting(Day19Product::id)
                .containsExactly("p06", "p07", "p08", "p09");
    }

    @Test
    void searchSortsByPriceAscending() {
        List<Day19Product> products = catalog.search("телевизор", null, null, "price_asc");

        assertThat(products).extracting(Day19Product::id).containsExactly("p11", "p10");
        assertThat(products.get(0).price()).isLessThan(products.get(1).price());
    }

    @Test
    void searchSortsByRatingDescending() {
        List<Day19Product> products = catalog.search("ноутбук", null, null, "rating");

        assertThat(products.get(0).id()).isEqualTo("p05");
    }

    @Test
    void searchAppliesMaxResultsLimit() {
        List<Day19Product> products = catalog.search("ноутбук", null, 3, null);

        assertThat(products).hasSize(3);
    }

    @Test
    void searchIgnoresCaseInQueryAndCategory() {
        List<Day19Product> products = catalog.search("НОУТБУК", "НОУТБУКИ", null, null);

        assertThat(products).hasSize(5);
    }

    @Test
    void searchFindsNothingForUnknownQuery() {
        List<Day19Product> products = catalog.search("пылесос", null, null, null);

        assertThat(products).isEmpty();
    }

    @Test
    void parseJsonRoundTripsSearchResult() {
        List<Day19Product> found = catalog.search("ноутбук", null, null, null);
        String json = catalogJson(found);

        List<Day19Product> parsed = catalog.parseJson(json);

        assertThat(parsed).hasSize(5);
        Day19Product first = parsed.get(0);
        assertThat(first.id()).isEqualTo("p01");
        assertThat(first.title()).isEqualTo("Ноутбук Lenovo IdeaPad 3");
        assertThat(first.price()).isEqualTo(54990.0);
        assertThat(first.currency()).isEqualTo("RUB");
        assertThat(first.params()).containsEntry("Память", "16 ГБ");
    }

    @Test
    void parseJsonRejectsEmptySearchResult() {
        List<Day19Product> found = catalog.search("пылесос", null, null, null);
        String json = catalogJson(found);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> catalog.parseJson(json))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не содержит товаров");
    }

    @Test
    void parseJsonRejectsBlankInput() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> catalog.parseJson(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("пуст");
    }

    @Test
    void parseJsonRejectsMalformedJson() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> catalog.parseJson("{\"products\":["))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("некорректен");
    }

    private static String catalogJson(List<Day19Product> products) {
        return new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .putPOJO("products", products).toString();
    }
}