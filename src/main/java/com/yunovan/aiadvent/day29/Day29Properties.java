package com.yunovan.aiadvent.day29;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "day29")
public record Day29Properties(
        Integer benchmarkRuns,
        Integer questionsLimit,
        Double tunedTemperature,
        Integer tunedNumCtx,
        Integer tunedNumPredict) {

    public static final int DEFAULT_BENCHMARK_RUNS = 1;
    public static final int DEFAULT_QUESTIONS_LIMIT = 10;
    public static final double DEFAULT_TUNED_TEMPERATURE = 0.1;
    public static final int DEFAULT_TUNED_NUM_CTX = 2048;
    public static final int DEFAULT_TUNED_NUM_PREDICT = 240;

    public Day29Properties {
        benchmarkRuns = benchmarkRuns == null || benchmarkRuns <= 0
                ? DEFAULT_BENCHMARK_RUNS : benchmarkRuns;
        questionsLimit = questionsLimit == null || questionsLimit <= 0
                ? DEFAULT_QUESTIONS_LIMIT : Math.min(questionsLimit, 10);
        tunedTemperature = tunedTemperature == null || tunedTemperature.isNaN()
                || tunedTemperature < 0.0 || tunedTemperature > 2.0
                ? DEFAULT_TUNED_TEMPERATURE : tunedTemperature;
        tunedNumCtx = tunedNumCtx == null || tunedNumCtx <= 0
                ? DEFAULT_TUNED_NUM_CTX : tunedNumCtx;
        tunedNumPredict = tunedNumPredict == null || tunedNumPredict <= 0
                ? DEFAULT_TUNED_NUM_PREDICT : tunedNumPredict;
    }
}
