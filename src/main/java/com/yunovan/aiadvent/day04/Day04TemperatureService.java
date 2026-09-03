package com.yunovan.aiadvent.day04;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class Day04TemperatureService {

    private final LlmClient llmClient;
    private final LlmProperties properties;

    public Day04TemperatureService(LlmClient llmClient, LlmProperties properties) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    public TemperatureResponse compare(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        String trimmed = prompt.trim();

        List<TemperatureSample> samples = new ArrayList<>();
        for (double temperature : Day04Temperatures.VALUES) {
            LlmReply reply = llmClient.complete(CompletionCommand.withTemperature(trimmed, temperature));
            samples.add(TemperatureSample.from(temperature, reply.content(), reply.finishReason()));
        }
        return new TemperatureResponse(trimmed, properties.model(), samples, Day04Temperatures.CONCLUSIONS);
    }
}
