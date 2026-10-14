package com.yunovan.aiadvent.day32;

public record Day32ReviewRequest(
        String base,
        String head,
        String diff,
        String title,
        String description) {
}
