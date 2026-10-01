package com.yunovan.aiadvent.day22;

import java.util.List;

public record Day22ControlQuestion(
        String id,
        String question,
        List<String> expectedKeywords,
        List<String> expectedSources) {
}