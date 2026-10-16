package com.yunovan.aiadvent.day34;

public record Day34Artifact(String path, String action, String content, int bytes, String diff) {

    public boolean changed() {
        return "create".equals(action) || "update".equals(action);
    }
}
