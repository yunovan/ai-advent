package com.yunovan.aiadvent.day04;

import java.util.List;

public record TemperatureResponse(String prompt, String model, List<TemperatureSample> samples, String conclusions) {
}
