package com.yunovan.aiadvent.day24;

import java.util.List;

public record Day24ControlQuestion(
        String id,
        String question,
        List<String> expectedKeywords,
        List<String> expectedSources) {
}