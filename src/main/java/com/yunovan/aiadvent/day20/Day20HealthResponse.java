package com.yunovan.aiadvent.day20;

import java.util.List;

public record Day20HealthResponse(
        boolean connected,
        List<Day20ServerInfo> servers,
        int toolCount) {
}