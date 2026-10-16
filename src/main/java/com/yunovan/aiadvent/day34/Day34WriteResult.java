package com.yunovan.aiadvent.day34;

public record Day34WriteResult(String path, String action, int bytes) {

    public boolean written() {
        return "create".equals(action) || "update".equals(action);
    }
}
