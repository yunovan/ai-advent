package com.yunovan.aiadvent.day14;

public class Day14InvariantNotFoundException extends RuntimeException {

    public Day14InvariantNotFoundException(String invariantId) {
        super("Инвариант '" + invariantId + "' не найден");
    }
}