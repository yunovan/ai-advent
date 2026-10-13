package com.yunovan.aiadvent.day31;

import java.util.List;

public record Day31ToolsResponse(
        boolean available,
        String endpoint,
        int count,
        List<Day31ToolInfo> tools,
        String reason) {
}
