package com.yunovan.aiadvent.day29;

import java.util.List;

public record Day29AskResponse(
        String question,
        List<String> sources,
        int promptCharsBaseline,
        int promptCharsTuned,
        Day29AnswerResponse baseline,
        Day29AnswerResponse tuned,
        String qualityVerdict,
        String speedVerdict) {
}
