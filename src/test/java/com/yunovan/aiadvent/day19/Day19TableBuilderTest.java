package com.yunovan.aiadvent.day19;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Day19TableBuilderTest {

    private final Day19CatalogService catalog = new Day19CatalogService();

    @Test
    void markdownContainsHeaderWithProductCount() {
        List<Day19Product> products = catalog.search("ноутбук", null, null, null);

        String table = Day19TableBuilder.markdown("ноутбук", products);

        assertThat(table).startsWith("Сравнение по запросу «ноутбук» — товаров: 5");
        assertThat(table).contains("Товар | Продавец | Цена, ₽ | Рейтинг | Ключевые параметры | Ссылка");
        assertThat(table).contains("Ноутбук Lenovo IdeaPad 3 | DNS | 54 990 | 4.6");
    }

    @Test
    void markdownContainsLinkToSellerWebsite() {
        List<Day19Product> products = catalog.search("ноутбук", null, null, null);

        String table = Day19TableBuilder.markdown("ноутбук", products);

        assertThat(table).contains("[DNS](https://www.dns-shop.ru/lenovo-ideapad-3)");
    }

    @Test
    void markdownEscapesPipesInParams() {
        List<Day19Product> products = List.of(catalog.search("ноутбук", null, null, null).get(0));

        String table = Day19TableBuilder.markdown("ноутбук", products);

        assertThat(table).doesNotContain("| 15");
        assertThat(table).contains("Экран: 15");
    }

    @Test
    void csvHasHeaderAndRows() {
        List<Day19Product> products = catalog.search("телевизоры", null, null, "price_asc");

        String csv = Day19TableBuilder.csv(products);

        assertThat(csv).startsWith("Товар;Продавец;Цена;Рейтинг;Параметры;Ссылка\n");
        assertThat(csv).contains("Телевизор LG");
        assertThat(csv).contains("47990.0;4.4");
        assertThat(csv).contains("https://www.mvideo.ru/lg-50ur7800");
    }

    @Test
    void csvFieldSeparatorInsideParamsIsReplaced() {
        List<Day19Product> products = catalog.search("ноутбук", null, 1, null);

        String csv = Day19TableBuilder.csv(products);

        String[] lines = csv.split("\n");
        for (int i = 1; i < lines.length; i++) {
            long separators = lines[i].chars().filter(c -> c == ';').count();
            assertThat(separators).as("колонок в строке %d", i).isEqualTo(5);
        }
    }
}