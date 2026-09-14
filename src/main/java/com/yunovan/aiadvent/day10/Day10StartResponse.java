package com.yunovan.aiadvent.day10;

import com.yunovan.aiadvent.agent.dialog.DialogMemory;
import java.time.Instant;
import java.util.List;

public record Day10StartResponse(
        String dialogId,
        Instant createdAt,
        Day10Strategy strategy,
        int windowSize,
        List<Day10Fact> facts,
        List<Day10Branch> branches,
        String activeBranchId,
        List<DialogMemory> memory) {
}