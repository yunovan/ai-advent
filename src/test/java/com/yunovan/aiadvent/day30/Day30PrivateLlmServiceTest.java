package com.yunovan.aiadvent.day30;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26MockOllama;
import com.yunovan.aiadvent.day26.Day26Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class Day30PrivateLlmServiceTest {

    private Day26MockOllama ollama;
    private Day26Properties localProperties;
    private Day26LocalLlmClient localLlm;

    @BeforeEach
    void setUp() {
        ollama = Day26MockOllama.start();
        localProperties = new Day26Properties(ollama.endpoint(), null, null, null, null, null);
        localLlm = new Day26LocalLlmClient(RestClient.builder(), localProperties);
    }

    @AfterEach
    void tearDown() {
        ollama.close();
    }

    private static Day30Properties props(Integer maxMessages, Integer maxPromptChars, Integer rate,
                                         Integer concurrent, Integer maxSessions, String apiKey) {
        return new Day30Properties(null, null, apiKey, null, maxMessages, maxPromptChars,
                rate, concurrent, maxSessions, null, null, null, null);
    }

    private Day30PrivateLlmService service() {
        return service(props(null, null, null, null, null, null));
    }

    private Day30PrivateLlmService service(Day30Properties properties) {
        return new Day30PrivateLlmService(localLlm, localProperties, properties);
    }

    @Test
    void healthExposesServiceUrlModelLimitsAndStats() {
        Day30HealthResponse health = service().health();

        assertThat(health.status()).isEqualTo("ok");
        assertThat(health.serviceUrl()).isEqualTo("http://localhost:8080");
        assertThat(health.networkUrls()).isNotEmpty();
        assertThat(health.model().available()).isTrue();
        assertThat(health.model().installed()).isTrue();
        assertThat(health.model().quantizationLevel()).isEqualTo("Q4_K_M");
        assertThat(health.model().contextLength()).isEqualTo(32_768L);
        assertThat(health.limits().maxMessages()).isEqualTo(12);
        assertThat(health.limits().maxPromptChars()).isEqualTo(6000);
        assertThat(health.limits().rateLimitPerMinute()).isEqualTo(10);
        assertThat(health.limits().maxConcurrent()).isEqualTo(4);
        assertThat(health.limits().apiKeyRequired()).isFalse();
        assertThat(health.stats().totalRequests()).isZero();
        assertThat(health.stats().avgLatencyMs()).isNull();
    }

    @Test
    void healthIsDegradedWhenServerStopped() {
        ollama.close();

        Day30HealthResponse health = service().health();

        assertThat(health.status()).isEqualTo("degraded");
        assertThat(health.model().available()).isFalse();
        assertThat(health.model().reason()).contains("недоступен");
    }

    @Test
    void chatKeepsSessionAndReportsMetrics() {
        Day30PrivateLlmService service = service();

        Day30ChatResponse first = service.chat(
                new Day30ChatRequest(null, "Кто ты?"), "cli", null);
        assertThat(first.sessionId()).isNotBlank();
        assertThat(first.turn()).isEqualTo(1);
        assertThat(first.reply()).isEqualTo(Day26MockOllama.CYRILLIC_REPLY);
        assertThat(first.contextMessages()).isEqualTo(2);
        assertThat(first.outputTokens()).isPositive();
        assertThat(first.rateRemaining()).isEqualTo(9);
        assertThat(first.model()).isEqualTo(Day26MockOllama.MODEL_TAG);

        Day30ChatResponse second = service.chat(
                new Day30ChatRequest(first.sessionId(), "А на какой модели?"), "cli", null);
        assertThat(second.sessionId()).isEqualTo(first.sessionId());
        assertThat(second.turn()).isEqualTo(2);
        assertThat(second.contextMessages()).isEqualTo(4);
        assertThat(second.rateRemaining()).isEqualTo(8);

        Day30HealthResponse health = service.health();
        assertThat(health.stats().totalRequests()).isEqualTo(2);
        assertThat(health.stats().acceptedRequests()).isEqualTo(2);
        assertThat(health.stats().sessions()).isEqualTo(1);
        assertThat(health.stats().avgLatencyMs()).isNotNull();
    }

    @Test
    void chatRejectsBlankMessage() {
        assertThatThrownBy(() -> service().chat(new Day30ChatRequest(null, "  "), "cli", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("пустым");
    }

    @Test
    void chatRejectsMessageLongerThanContextLimit() {
        Day30PrivateLlmService service = service(props(null, 200, null, null, null, null));

        assertThatThrownBy(() -> service.chat(
                new Day30ChatRequest(null, "a".repeat(201)), "cli", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("контекста");
    }

    @Test
    void chatRejectsWhenRateLimitExceeded() {
        Day30PrivateLlmService service = service(props(null, null, 2, null, null, null));

        service.chat(new Day30ChatRequest(null, "раз"), "cli", null);
        service.chat(new Day30ChatRequest(null, "два"), "cli", null);
        assertThatThrownBy(() -> service.chat(new Day30ChatRequest(null, "три"), "cli", null))
                .isInstanceOf(Day30RateLimitException.class)
                .hasMessageContaining("лимит");

        Day30HealthResponse health = service.health();
        assertThat(health.stats().rejectedByRateLimit()).isEqualTo(1);
    }

    @Test
    void chatRequiresApiKeyWhenConfigured() {
        Day30PrivateLlmService service = service(props(null, null, null, null, null, "secret"));

        assertThatThrownBy(() -> service.chat(new Day30ChatRequest(null, "привет"), "cli", null))
                .isInstanceOf(Day30AuthException.class);
        assertThatThrownBy(() -> service.chat(new Day30ChatRequest(null, "привет"), "cli", "wrong"))
                .isInstanceOf(Day30AuthException.class);

        Day30ChatResponse response = service.chat(
                new Day30ChatRequest(null, "привет"), "cli", "secret");
        assertThat(response.reply()).isNotBlank();
    }

    @Test
    void stressProcessesConcurrentRequests() {
        Day30StressResponse stress = service().stress(null, "cli", null);

        assertThat(stress.requests()).isEqualTo(6);
        assertThat(stress.concurrency()).isEqualTo(3);
        assertThat(stress.succeeded()).isEqualTo(6);
        assertThat(stress.failed()).isZero();
        assertThat(stress.rateLimited()).isZero();
        assertThat(stress.items()).hasSize(6);
        assertThat(stress.items()).allSatisfy(item -> {
            assertThat(item.ok()).isTrue();
            assertThat(item.error()).isEmpty();
        });
        assertThat(stress.avgLatencyMs()).isGreaterThanOrEqualTo(0);
        assertThat(stress.verdict()).contains("выдержал нагрузку");
    }
}
