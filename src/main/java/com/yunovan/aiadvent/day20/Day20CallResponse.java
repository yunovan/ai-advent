package com.yunovan.aiadvent.day20;

import java.util.Map;

public record Day20CallResponse(
        String server,
        String tool,
        Map<String, Object> arguments,
        boolean success,
        String result) {
}