package com.yunovan.aiadvent.day17;

public record Day17Ticket(
        String id,
        String title,
        String description,
        String assignee,
        String status,
        String createdAt) {
}