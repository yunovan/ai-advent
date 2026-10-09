package com.yunovan.aiadvent.day30;

import com.yunovan.aiadvent.day29.Day29ModelReport;
import java.util.List;

public record Day30HealthResponse(
        String status,
        String serviceUrl,
        String host,
        int port,
        List<String> networkUrls,
        Day29ModelReport model,
        Day30Limits limits,
        Day30Stats stats) {
}
