package com.yunovan.aiadvent.day19;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class Day19TableBuilder {

    private static final String[] COLUMNS = {"Товар", "Продавец", "Цена, ₽", "Рейтинг", "Ключевые параметры", "Ссылка"};

    private Day19TableBuilder() {
    }

    public static String markdown(String query, List<Day19Product> products) {
        StringBuilder table = new StringBuilder();
        table.append("Сравнение по запросу «").append(query).append("» — товаров: ").append(products.size());
        table.append("\n\n").append("| ").append(String.join(" | ", COLUMNS)).append(" |\n");
        table.append("|").append("---|".repeat(COLUMNS.length)).append("\n");
        for (Day19Product product : products) {
            table.append("| ").append(product.title())
                    .append(" | ").append(product.seller())
                    .append(" | ").append(formatPrice(product.price()))
                    .append(" | ").append(formatRating(product.rating()))
                    .append(" | ").append(flattenParams(product.params()).replace('|', '/'))
                    .append(" | [").append(product.seller()).append("](").append(product.website()).append(") |\n");
        }
        return table.toString();
    }

    public static String csv(List<Day19Product> products) {
        StringBuilder csv = new StringBuilder();
        csv.append("Товар;Продавец;Цена;Рейтинг;Параметры;Ссылка\n");
        for (Day19Product product : products) {
            csv.append(product.title()).append(';')
                    .append(product.seller()).append(';')
                    .append(product.price()).append(';')
                    .append(product.rating()).append(';')
                    .append(flattenParams(product.params()).replace(';', ',')).append(';')
                    .append(product.website()).append('\n');
        }
        return csv.toString();
    }

    private static String flattenParams(Map<String, String> params) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (text.length() > 0) {
                text.append("; ");
            }
            text.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return text.toString();
    }

    private static String formatPrice(double price) {
        return String.format(Locale.ROOT, "%,.0f", price).replace(',', ' ');
    }

    private static String formatRating(double rating) {
        return String.format(Locale.ROOT, "%.1f", rating);
    }
}