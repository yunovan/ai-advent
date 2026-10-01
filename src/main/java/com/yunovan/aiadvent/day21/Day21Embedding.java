package com.yunovan.aiadvent.day21;

public record Day21Embedding(float[] values) {

    public int dimensions() {
        return values.length;
    }
}