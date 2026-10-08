package com.yunovan.aiadvent.day28;

public record Day28HealthResponse(
        int documents,
        int corpusChars,
        int pagesEstimate,
        String strategy,
        int topKBefore,
        int topKAfter,
        double threshold,
        boolean rewrite,
        int answerMaxTokens,
        int evaluateRuns,
        String retrieval,
        String localEndpoint,
        String localModel,
        boolean localAvailable,
        String localVersion,
        boolean modelInstalled,
        boolean cloudConfigured,
        String cloudModel,
        boolean usesCloud,
        String localError) {

    public Day28HealthResponse {
        retrieval = retrieval == null ? "" : retrieval;
        localEndpoint = localEndpoint == null ? "" : localEndpoint;
        localModel = localModel == null ? "" : localModel;
        localVersion = localVersion == null ? "" : localVersion;
        cloudModel = cloudModel == null ? "" : cloudModel;
        localError = localError == null ? "" : localError;
    }
}
