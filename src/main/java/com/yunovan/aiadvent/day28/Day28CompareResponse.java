package com.yunovan.aiadvent.day28;

import java.util.List;

public record Day28CompareResponse(
        String question,
        Day28AnswerResponse local,
        Day28AnswerResponse cloud,
        boolean cloudConfigured,
        List<String> sources,
        String verdict) {

    public Day28CompareResponse {
        question = question == null ? "" : question;
        sources = sources == null ? List.of() : sources;
        verdict = verdict == null ? "" : verdict;
    }
}
