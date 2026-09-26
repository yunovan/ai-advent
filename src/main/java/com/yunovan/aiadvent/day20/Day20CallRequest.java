package com.yunovan.aiadvent.day20;

import java.util.Map;

public record Day20CallRequest(
        String server,
        String tool,
        Map<String, Object> arguments) {
}