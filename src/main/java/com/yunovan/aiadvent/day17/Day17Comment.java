package com.yunovan.aiadvent.day17;

public record Day17Comment(
        String id,
        String taskId,
        String author,
        String text,
        String createdAt) {
}