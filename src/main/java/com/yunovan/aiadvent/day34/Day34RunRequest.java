package com.yunovan.aiadvent.day34;

public record Day34RunRequest(
        String goal,
        String task,
        String target,
        Boolean write,
        String context) {
}
