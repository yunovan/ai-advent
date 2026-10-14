package com.yunovan.aiadvent.day33;

import java.util.List;

public record Day33Ticket(
        String id,
        String userId,
        String subject,
        String status,
        String priority,
        String product,
        String createdAt,
        String updatedAt,
        List<String> tags,
        List<Day33TicketMessage> messages) {
}
