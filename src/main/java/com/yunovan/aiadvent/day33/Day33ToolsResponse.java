package com.yunovan.aiadvent.day33;

import java.util.List;

public record Day33ToolsResponse(
        boolean available,
        String endpoint,
        int count,
        List<Day33ToolInfo> tools,
        String reason) {
}
