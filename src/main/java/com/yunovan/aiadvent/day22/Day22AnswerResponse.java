package com.yunovan.aiadvent.day22;

import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.List;

public record Day22AnswerResponse(
        String question,
        String mode,
        List<Day21SearchHit> retrievedHits,
        String answer,
        boolean fallback) {
}