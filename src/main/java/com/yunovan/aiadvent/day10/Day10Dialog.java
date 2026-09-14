package com.yunovan.aiadvent.day10;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record Day10Dialog(
        String id,
        Instant createdAt,
        Instant finishedAt,
        String summary,
        Day10Strategy strategy,
        int windowSize,
        List<Day10Fact> facts,
        Integer checkpointMessageIndex,
        List<Day10Branch> branches,
        String activeBranchId) {

    public static Day10Dialog create(Day10Strategy strategy, int windowSize) {
        return new Day10Dialog(
                UUID.randomUUID().toString(),
                Instant.now(),
                null,
                null,
                strategy,
                Math.max(1, windowSize),
                List.of(),
                null,
                List.of(Day10Branch.main()),
                "main");
    }

    public Day10Branch activeBranch() {
        for (Day10Branch branch : branches) {
            if (branch.id().equals(activeBranchId)) {
                return branch;
            }
        }
        if (branches.isEmpty()) {
            return Day10Branch.main();
        }
        return branches.getFirst();
    }

    public Day10Dialog withBranchMessages(String branchId, List<ConversationMessage> messages) {
        List<Day10Branch> updated = new ArrayList<>();
        for (Day10Branch branch : branches) {
            updated.add(branch.id().equals(branchId) ? branch.withMessages(messages) : branch);
        }
        return new Day10Dialog(
                id, createdAt, finishedAt, summary, strategy, windowSize, facts,
                checkpointMessageIndex, updated, activeBranchId);
    }

    public Day10Dialog withWindow(int newWindowSize) {
        return new Day10Dialog(
                id, createdAt, finishedAt, summary, strategy, Math.max(1, newWindowSize), facts,
                checkpointMessageIndex, branches, activeBranchId);
    }

    public Day10Dialog withFacts(List<Day10Fact> newFacts) {
        return new Day10Dialog(
                id, createdAt, finishedAt, summary, strategy, windowSize, List.copyOf(newFacts),
                checkpointMessageIndex, branches, activeBranchId);
    }

    public Day10Dialog withCheckpoint(int index) {
        return new Day10Dialog(
                id, createdAt, finishedAt, summary, strategy, windowSize, facts,
                index, branches, activeBranchId);
    }

    public Day10Dialog withActiveBranch(String branchId) {
        for (Day10Branch branch : branches) {
            if (branch.id().equals(branchId)) {
                return new Day10Dialog(
                        id, createdAt, finishedAt, summary, strategy, windowSize, facts,
                        checkpointMessageIndex, branches, branchId);
            }
        }
        throw new IllegalArgumentException("Ветка '" + branchId + "' не найдена в диалоге '"
                + id + "'. Доступные ветки: " + branches.stream().map(Day10Branch::id).toList());
    }

    public Day10Dialog withNewBranch(Day10Branch newBranch) {
        List<Day10Branch> updated = new ArrayList<>(branches);
        updated.add(newBranch);
        return new Day10Dialog(
                id, createdAt, finishedAt, summary, strategy, windowSize, facts,
                checkpointMessageIndex, updated, newBranch.id());
    }

    public Day10Dialog finished(String newSummary, Instant at) {
        return new Day10Dialog(
                id, createdAt, at, newSummary, strategy, windowSize, facts,
                checkpointMessageIndex, branches, activeBranchId);
    }

    public boolean isFinished() {
        return finishedAt != null;
    }

    public List<ConversationMessage> activeMessages() {
        return activeBranch().messages();
    }
}