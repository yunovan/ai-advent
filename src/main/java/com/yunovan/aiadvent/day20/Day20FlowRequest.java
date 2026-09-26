package com.yunovan.aiadvent.day20;

import java.util.Map;

public record Day20FlowRequest(
        String flow,
        Map<String, Object> arguments) {
}