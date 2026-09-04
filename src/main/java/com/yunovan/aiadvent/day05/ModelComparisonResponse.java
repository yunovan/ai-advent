package com.yunovan.aiadvent.day05;

import java.util.List;

public record ModelComparisonResponse(
        String prompt, List<ModelRun> runs, String conclusion, List<ModelLink> links) {
}
