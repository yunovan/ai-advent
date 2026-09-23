package com.yunovan.aiadvent.day16;

import java.util.Map;

public record Day16CallRequest(String tool, Map<String, Object> arguments) {
}