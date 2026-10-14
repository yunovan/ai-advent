package com.yunovan.aiadvent.day33;

import java.util.List;

public record Day33TicketInfo(
        String id,
        String userId,
        String subject,
        String status,
        String priority,
        String product,
        String updatedAt,
        List<String> tags) {
}
