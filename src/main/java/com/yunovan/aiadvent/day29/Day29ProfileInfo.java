package com.yunovan.aiadvent.day29;

public record Day29ProfileInfo(
        String id,
        String title,
        double temperature,
        int maxTokens,
        Integer numCtx,
        String promptTemplate,
        String description) {

    public boolean tuned() {
        return "tuned".equals(id);
    }
}
