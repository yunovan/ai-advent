package com.yunovan.aiadvent.day19;

public record Day19SearchRequest(
        String query,
        String category,
        Integer maxResults,
        String sort) {
}