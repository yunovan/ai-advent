package com.yunovan.aiadvent.day23;

import java.util.List;

public record Day23CompareResponse(
        String question,
        Day23AnswerResponse first,
        Day23AnswerResponse second,
        List<String> sourcesBefore,
        List<String> sourcesAfter,
        String verdict) {
}