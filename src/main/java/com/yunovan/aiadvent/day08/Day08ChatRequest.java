package com.yunovan.aiadvent.day08;

public record Day08ChatRequest(String sessionId, String request, Long contextLimit) {

    public Day08ChatRequest(String sessionId, String request) {
        this(sessionId, request, null);
    }
}