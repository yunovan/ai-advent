package com.yunovan.aiadvent.day19;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class Day19CatalogService {

    static final List<Day19Product> CATALOG = List.of(
            product("p01", "Ноутбук Lenovo IdeaPad 3", "ноутбуки", "DNS",
                    "https://www.dns-shop.ru/lenovo-ideapad-3", 54990, 4.6, Map.ofEntries(
                            Map.entry("Процессор", "Intel Core i5-1235U"),
                            Map.entry("Память", "16 ГБ"),
                            Map.entry("Диск", "512 ГБ SSD"),
                            Map.entry("Экран", "15.6\" IPS"))),
            product("p02", "Ноутбук ASUS VivoBook 15", "ноутбуки", "Citilink",
                    "https://www.citilink.ru/asus-vivobook-15", 47990, 4.5, Map.ofEntries(
                            Map.entry("Процессор", "AMD Ryzen 5 7530U"),
                            Map.entry("Память", "16 ГБ"),
                            Map.entry("Диск", "512 ГБ SSD"),
                            Map.entry("Экран", "15.6\" IPS"))),
            product("p03", "Ноутбук Acer Aspire 5", "ноутбуки", "М.Видео",
                    "https://www.mvideo.ru/acer-aspire-5", 59990, 4.4, Map.ofEntries(
                            Map.entry("Процессор", "Intel Core i5-12450H"),
                            Map.entry("Память", "16 ГБ"),
                            Map.entry("Диск", "512 ГБ SSD"),
                            Map.entry("Экран", "15.6\" IPS"))),
            product("p04", "Ноутбук HP Pavilion 15", "ноутбуки", "Ozon",
                    "https://www.ozon.ru/hp-pavilion-15", 62990, 4.7, Map.ofEntries(
                            Map.entry("Процессор", "Intel Core i7-1255U"),
                            Map.entry("Память", "16 ГБ"),
                            Map.entry("Диск", "1 ТБ SSD"),
                            Map.entry("Экран", "15.6\" IPS"))),
            product("p05", "Ноутбук Apple MacBook Air 13 M2", "ноутбуки", "re:Store",
                    "https://restore.retail.ru/macbook-air-m2", 119990, 4.9, Map.ofEntries(
                            Map.entry("Процессор", "Apple M2"),
                            Map.entry("Память", "16 ГБ"),
                            Map.entry("Диск", "256 ГБ SSD"),
                            Map.entry("Экран", "13.6\" Liquid Retina"))),
            product("p06", "Смартфон Xiaomi Redmi Note 13 Pro", "смартфоны", "Wildberries",
                    "https://www.wildberries.ru/xiaomi-redmi-note-13", 23990, 4.3, Map.ofEntries(
                            Map.entry("Экран", "6.67\" AMOLED"),
                            Map.entry("Камера", "200 Мп"),
                            Map.entry("Память", "256 ГБ"),
                            Map.entry("Аккумулятор", "5100 мАч"))),
            product("p07", "Смартфон Samsung Galaxy A55", "смартфоны", "М.Видео",
                    "https://www.mvideo.ru/samsung-galaxy-a55", 34990, 4.5, Map.ofEntries(
                            Map.entry("Экран", "6.6\" AMOLED"),
                            Map.entry("Камера", "50 Мп"),
                            Map.entry("Память", "256 ГБ"),
                            Map.entry("Аккумулятор", "5000 мАч"))),
            product("p08", "Смартфон Google Pixel 8a", "смартфоны", "Citilink",
                    "https://www.citilink.ru/google-pixel-8a", 44990, 4.6, Map.ofEntries(
                            Map.entry("Экран", "6.1\" OLED"),
                            Map.entry("Камера", "64 Мп"),
                            Map.entry("Память", "128 ГБ"),
                            Map.entry("Аккумулятор", "4492 мАч"))),
            product("p09", "Смартфон Apple iPhone 15", "смартфоны", "Ozon",
                    "https://www.ozon.ru/apple-iphone-15", 79990, 4.8, Map.ofEntries(
                            Map.entry("Экран", "6.1\" OLED"),
                            Map.entry("Камера", "48 Мп"),
                            Map.entry("Память", "128 ГБ"),
                            Map.entry("Аккумулятор", "3349 мАч"))),
            product("p10", "Телевизор Samsung 55\" 4K", "телевизоры", "Citilink",
                    "https://www.citilink.ru/samsung-ue55du8000", 59990, 4.5, Map.ofEntries(
                            Map.entry("Диагональ", "55 дюймов"),
                            Map.entry("Разрешение", "4K"),
                            Map.entry("HDR", "HDR10+"),
                            Map.entry("ОС", "Tizen"))),
            product("p11", "Телевизор LG 50\" 4K", "телевизоры", "М.Видео",
                    "https://www.mvideo.ru/lg-50ur7800", 47990, 4.4, Map.ofEntries(
                            Map.entry("Диагональ", "50 дюймов"),
                            Map.entry("Разрешение", "4K"),
                            Map.entry("HDR", "HDR10"),
                            Map.entry("ОС", "webOS"))),
            product("p12", "Наушники JBL Tune 770NC", "наушники", "Wildberries",
                    "https://www.wildberries.ru/jbl-tune-770nc", 8990, 4.4, Map.ofEntries(
                            Map.entry("Тип", "накладные"),
                            Map.entry("Шумоподавление", "да"),
                            Map.entry("Bluetooth", "5.3"),
                            Map.entry("Автономность", "70 ч"))),
            product("p13", "Наушники Sony WH-CH520", "наушники", "DNS",
                    "https://www.dns-shop.ru/sony-wh-ch520", 5490, 4.5, Map.ofEntries(
                            Map.entry("Тип", "накладные"),
                            Map.entry("Шумоподавление", "нет"),
                            Map.entry("Bluetooth", "5.2"),
                            Map.entry("Автономность", "50 ч"))));

    private final ObjectMapper mapper = new ObjectMapper();

    public List<Day19Product> search(String query, String category, Integer maxResults, String sort) {
        String need = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String categoryFilter = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        List<Day19Product> result = new ArrayList<>();
        for (Day19Product product : CATALOG) {
            String title = product.title().toLowerCase(Locale.ROOT);
            String productCategory = product.category().toLowerCase(Locale.ROOT);
            boolean byQuery = need.isBlank() || title.contains(need) || productCategory.contains(need);
            boolean byCategory = categoryFilter.isBlank()
                    || productCategory.contains(categoryFilter) || categoryFilter.contains(productCategory);
            if (byQuery && byCategory) {
                result.add(product);
            }
        }
        List<Day19Product> sorted = new ArrayList<>(result);
        String order = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        switch (order) {
            case "price_asc", "price" -> sorted.sort(Comparator.comparingDouble(Day19Product::price));
            case "price_desc" -> sorted.sort(Comparator.comparingDouble(Day19Product::price).reversed());
            case "rating" -> sorted.sort(Comparator.comparingDouble(Day19Product::rating).reversed());
            default -> {
            }
        }
        int limit = maxResults == null || maxResults <= 0 ? Integer.MAX_VALUE : maxResults;
        return limit >= sorted.size() ? sorted : sorted.subList(0, limit);
    }

    public List<Day19Product> parseJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Поисковый результат пуст");
        }
        try {
            JsonNode root = mapper.readTree(json);
            JsonNode productsNode = root.path("products");
            if (!productsNode.isArray()) {
                throw new IllegalArgumentException("Поисковый результат не содержит products");
            }
            List<Day19Product> products = new ArrayList<>();
            for (JsonNode node : productsNode) {
                products.add(toProduct(node));
            }
            if (products.isEmpty()) {
                throw new IllegalArgumentException("Поисковый результат не содержит товаров");
            }
            return products;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Поисковый результат некорректен: " + ex.getMessage());
        }
    }

    private static Day19Product toProduct(JsonNode node) {
        Map<String, String> params = new LinkedHashMap<>();
        JsonNode paramsNode = node.path("params");
        if (paramsNode.isObject()) {
            paramsNode.fields().forEachRemaining(e -> params.put(e.getKey(), e.getValue().asText()));
        }
        return new Day19Product(
                node.path("id").asText(),
                node.path("title").asText(),
                node.path("category").asText(),
                node.path("seller").asText(),
                node.path("website").asText(),
                node.path("price").asDouble(),
                node.path("currency").asText("RUB"),
                node.path("rating").asDouble(),
                params);
    }

    private static Day19Product product(String id, String title, String category, String seller,
                                        String website, double price, double rating,
                                        Map<String, String> params) {
        return new Day19Product(id, title, category, seller, website, price, "RUB", rating, params);
    }
}