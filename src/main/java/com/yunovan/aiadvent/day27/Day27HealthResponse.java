package com.yunovan.aiadvent.day27;

public record Day27HealthResponse(
        String endpoint,
        String model,
        boolean available,
        String version,
        boolean modelInstalled,
        boolean usesCloud,
        int historyLimit,
        int maxSessions,
        int sessions,
        String error) {

    public Day27HealthResponse {
        version = version == null ? "" : version;
        error = error == null ? "" : error;
    }
}
