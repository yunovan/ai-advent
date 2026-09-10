package com.yunovan.aiadvent.day09;

public record Day09ChatRequest(String request, Long contextLimit, Boolean compression) {

    public Day09ChatRequest(String request) {
        this(request, null, null);
    }
}