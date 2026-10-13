package com.yunovan.aiadvent.day32;

import java.util.List;

public record Day32FileDiff(
        String path,
        String oldPath,
        String status,
        int additions,
        int deletions,
        int hunks,
        List<Day32Line> addedLines) {

    public boolean code() {
        return path.endsWith(".java") || path.endsWith(".kt") || path.endsWith(".kts");
    }
}
