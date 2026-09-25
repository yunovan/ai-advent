package com.yunovan.aiadvent.day18;

public record Day18CollectorRequest(
        String feed,
        Integer periodSeconds,
        String url,
        String sourceFeed) {
}