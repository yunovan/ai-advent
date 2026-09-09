package com.yunovan.aiadvent.day08;

public record Day08ChatRequest(String request, Long contextLimit) {

    public Day08ChatRequest(String request) {
        this(request, null);
    }
}