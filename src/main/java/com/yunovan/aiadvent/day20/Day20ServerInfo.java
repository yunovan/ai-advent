package com.yunovan.aiadvent.day20;

public record Day20ServerInfo(
        String server,
        String name,
        String version,
        int toolCount,
        boolean connected) {
}