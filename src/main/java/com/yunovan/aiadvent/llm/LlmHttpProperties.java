package com.yunovan.aiadvent.llm;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm.http")
public record LlmHttpProperties(
        boolean customizeClient, Duration connectTimeout, Duration readTimeout, int maxAttempts) {

    public static LlmHttpProperties disabled() {
        return new LlmHttpProperties(false, Duration.ofSeconds(15), Duration.ofSeconds(120), 1);
    }

    public Duration connectTimeoutOrDefault() {
        return connectTimeout == null ? Duration.ofSeconds(15) : connectTimeout;
    }

    public Duration readTimeoutOrDefault() {
        return readTimeout == null ? Duration.ofSeconds(120) : readTimeout;
    }

    public int attempts() {
        return maxAttempts < 1 ? 1 : maxAttempts;
    }
}
