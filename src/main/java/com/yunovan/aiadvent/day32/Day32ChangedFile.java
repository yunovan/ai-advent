package com.yunovan.aiadvent.day32;

public record Day32ChangedFile(
        String path,
        String status,
        int additions,
        int deletions) {

    public int changes() {
        return additions + deletions;
    }
}
