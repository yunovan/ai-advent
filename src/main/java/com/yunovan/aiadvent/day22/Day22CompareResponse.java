package com.yunovan.aiadvent.day22;

import java.util.List;

public record Day22CompareResponse(
        String question,
        Day22AnswerResponse rag,
        Day22AnswerResponse plain,
        List<String> retrievedSources,
        String verdict) {
}