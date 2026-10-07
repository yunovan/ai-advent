package com.yunovan.aiadvent.day26;

import java.util.List;

public record Day26HealthResponse(
        String endpoint,
        String model,
        boolean available,
        String version,
        boolean modelInstalled,
        List<Day26InstalledModel> installedModels,
        String error) {

    public Day26HealthResponse {
        installedModels = installedModels == null ? List.of() : List.copyOf(installedModels);
        version = version == null ? "" : version;
        error = error == null ? "" : error;
    }
}
