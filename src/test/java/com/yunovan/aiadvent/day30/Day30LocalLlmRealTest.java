package com.yunovan.aiadvent.day30;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class Day30LocalLlmRealTest {

    private Day30PrivateLlmService service;
    private Day30HealthResponse health;

    @BeforeEach
    void setUp() {
        String endpoint = System.getenv().getOrDefault("DAY26_ENDPOINT",
                Day26Properties.DEFAULT_ENDPOINT);
        Day26Properties localProperties = new Day26Properties(endpoint, null, null, null, null, null);
        Day26LocalLlmClient localLlm = new Day26LocalLlmClient(RestClient.builder(), localProperties);
        service = new Day30PrivateLlmService(localLlm, localProperties,
                new Day30Properties(null, null, null, null, null, null, null, null, null, null, null,
                        null, null));

        health = service.health();
        Assumptions.assumeTrue(health.model().available(),
                "Ollama не запущена на " + endpoint + " — реальный тест пропущен");
        Assumptions.assumeTrue(health.model().installed(),
                "модель " + health.model().model() + " не установлена — реальный тест пропущен");
    }

    @Test
    void healthReportsRunningPrivateService() {
        assertThat(health.status()).isEqualTo("ok");
        assertThat(health.serviceUrl()).startsWith("http://");
        assertThat(health.networkUrls()).isNotEmpty();
        assertThat(health.model().quantizationLevel()).isNotBlank();
        assertThat(health.model().contextLength()).isPositive();
        assertThat(health.limits().maxMessages()).isEqualTo(12);
        assertThat(health.limits().rateLimitPerMinute()).isEqualTo(10);
        assertThat(health.stats().totalRequests()).isZero();
    }

    @Test
    void chatAnswersAndKeepsSessionContext() {
        Day30ChatResponse first = service.chat(
                new Day30ChatRequest(null, "Привет! Ответь одним предложением, кто ты."),
                "real-test", null);

        assertThat(first.sessionId()).isNotBlank();
        assertThat(first.turn()).isEqualTo(1);
        assertThat(first.reply()).isNotBlank();
        assertThat(first.contextMessages()).isEqualTo(2);
        assertThat(first.outputTokens()).isPositive();
        assertThat(first.tokensPerSecond()).isPositive();
        assertThat(first.rateRemaining()).isEqualTo(9);

        Day30ChatResponse second = service.chat(
                new Day30ChatRequest(first.sessionId(), "Повтори, о чём мы говорили, коротко."),
                "real-test", null);

        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(second.turn()).isEqualTo(2);
        assertThat(second.contextMessages()).isEqualTo(4);
        assertThat(second.reply()).isNotBlank();

        Day30HealthResponse after = service.health();
        assertThat(after.stats().totalRequests()).isEqualTo(2);
        assertThat(after.stats().acceptedRequests()).isEqualTo(2);
        assertThat(after.stats().sessions()).isEqualTo(1);
        assertThat(after.stats().avgLatencyMs()).isNotNull();
    }

    @Test
    void stressKeepsServiceStableUnderSeveralRequests() {
        Day30StressResponse stress = service.stress(
                new Day30StressRequest(3, 3), "real-test", null);

        assertThat(stress.requests()).isEqualTo(3);
        assertThat(stress.succeeded()).isEqualTo(3);
        assertThat(stress.failed()).isZero();
        assertThat(stress.items()).allSatisfy(item -> assertThat(item.ok()).isTrue());
        assertThat(stress.verdict()).contains("выдержал нагрузку");
    }
}
