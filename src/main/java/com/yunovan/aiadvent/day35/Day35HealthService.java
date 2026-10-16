package com.yunovan.aiadvent.day35;

import com.yunovan.aiadvent.day26.Day26InstalledModel;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class Day35HealthService {

    private final Day26LocalLlmClient llm;
    private final Day35Properties properties;

    public Day35HealthService(Day26LocalLlmClient llm, Day35Properties properties) {
        this.llm = llm;
        this.properties = properties;
    }

    public Day35Health health() {
        try {
            String model = firstModelName();
            return new Day35Health(true, properties.name(), properties.version(), model, "");
        } catch (RuntimeException ex) {
            return new Day35Health(false, properties.name(), properties.version(), "", ex.getMessage());
        }
    }

    private String firstModelName() {
        List<Day26InstalledModel> models = llm.models();
        return models.isEmpty() ? "" : models.get(0).name();
    }

    public record Day35Health(boolean available, String name, String version, String model, String detail) {
    }
}
