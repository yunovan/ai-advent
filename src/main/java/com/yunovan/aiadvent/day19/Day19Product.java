package com.yunovan.aiadvent.day19;

import java.util.Map;

public record Day19Product(
        String id,
        String title,
        String category,
        String seller,
        String website,
        double price,
        String currency,
        double rating,
        Map<String, String> params) {

    public Day19Product {
        currency = currency == null || currency.isBlank() ? "RUB" : currency;
    }
}