package com.yunovan.aiadvent.day26;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Day26LocalLlmRealTest {

    private static Day26Service service;

    @BeforeAll
    static void prepare() {
        String endpoint = System.getenv().getOrDefault("DAY26_ENDPOINT",
                Day26Properties.DEFAULT_ENDPOINT);
        Day26Properties properties = new Day26Properties(endpoint, null, null, null, null, null);
        service = new Day26Service(
                new Day26LocalLlmClient(org.springframework.web.client.RestClient.builder(), properties),
                properties);

        Day26HealthResponse health = service.health();
        Assumptions.assumeTrue(health.available(),
                "Ollama не запущен на " + endpoint + " — реальный тест пропущен");
        Assumptions.assumeTrue(health.modelInstalled(),
                "модель " + health.model() + " не установлена — реальный тест пропущен");
    }

    @Test
    void healthDetectsRunningLocalLlm() {
        Day26HealthResponse health = service.health();

        assertThat(health.available()).isTrue();
        assertThat(health.version()).isNotBlank();
        assertThat(health.modelInstalled()).isTrue();
        assertThat(health.installedModels()).isNotEmpty();
    }

    @Test
    void threeRequestsOfDifferentComplexityGetRealAnswers() {
        Day26RunReport report = service.run();

        assertThat(report.total()).isEqualTo(3);
        assertThat(report.okCount())
                .as("все три запроса должны получить ответ локальной модели")
                .isEqualTo(3);
        assertThat(report.failureCount()).isZero();
        assertThat(report.verdict()).contains("все 3 запросов выполнены");

        assertThat(report.results()).extracting(Day26TaskResult::complexity)
                .containsExactly("simple", "medium", "complex");
        assertThat(report.results()).allSatisfy(result -> {
            assertThat(result.ok()).isTrue();
            assertThat(result.reply()).isNotBlank();
            assertThat(result.promptTokens()).isPositive();
            assertThat(result.outputTokens()).isPositive();
            assertThat(result.latencyMs()).isPositive();
            assertThat(result.model()).isNotBlank();
        });
    }
}
