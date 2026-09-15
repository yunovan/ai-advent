package com.yunovan.aiadvent.day12;

import java.util.List;

public record Day12CreateProfileRequest(
        String name,
        String style,
        String format,
        List<String> restrictions,
        String notes) {
}