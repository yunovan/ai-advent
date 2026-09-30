package com.yunovan.aiadvent.day23;

import java.util.List;

public record Day23ControlQuestion(
        String id,
        String question,
        List<String> expectedKeywords,
        List<String> expectedSources) {
}