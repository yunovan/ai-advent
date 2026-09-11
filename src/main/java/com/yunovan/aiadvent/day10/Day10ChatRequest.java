package com.yunovan.aiadvent.day10;

public record Day10ChatRequest(String request, Long contextLimit, Integer windowSize) {
}