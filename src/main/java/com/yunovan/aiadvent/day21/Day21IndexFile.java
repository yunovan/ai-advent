package com.yunovan.aiadvent.day21;

public record Day21IndexFile(
        String strategy,
        int documents,
        int corpusChars,
        java.util.List<Day21IndexEntry> entries) {
}