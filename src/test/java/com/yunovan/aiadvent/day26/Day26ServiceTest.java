package com.yunovan.aiadvent.day26;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day26ServiceTest {

    private Day26MockOllama mock;
    private Day26Service service;

    @BeforeEach
    void setUp() {
        mock = Day26MockOllama.start();
        service = new Day26Service(
                new Day26LocalLlmClient(org.springframework.web.client.RestClient.builder(),
                        new Day26Properties(mock.endpoint(), null, null, null, null, null)),
                new Day26Properties(mock.endpoint(), null, null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        mock.close();
    }

    @Test
    void healthReportsRunningServerWithInstalledModel() {
        Day26HealthResponse health = service.health();

        assertThat(health.available()).isTrue();
        assertThat(health.version()).isEqualTo(Day26MockOllama.VERSION);
        assertThat(health.modelInstalled()).isTrue();
        assertThat(health.installedModels()).hasSize(1);
        assertThat(health.installedModels().getFirst().name()).isEqualTo(Day26MockOllama.MODEL_TAG);
        assertThat(health.error()).isEmpty();
    }

    @Test
    void healthDoesNotThrowWhenServerIsStopped() {
        mock.close();

        Day26HealthResponse health = service.health();

        assertThat(health.available()).isFalse();
        assertThat(health.version()).isEmpty();
        assertThat(health.modelInstalled()).isFalse();
        assertThat(health.installedModels()).isEmpty();
        assertThat(health.error()).contains("недоступен");
    }

    @Test
    void tasksCoverThreeComplexityLevels() {
        assertThat(Day26Task.ALL).hasSize(3);
        assertThat(service.tasks()).extracting(Day26Task::complexity)
                .containsExactly("simple", "medium", "complex");
        assertThat(service.tasks()).allSatisfy(task ->
                assertThat(task.prompt()).isNotBlank());
    }

    @Test
    void runExecutesAllThreeRequests() {
        Day26RunReport report = service.run();

        assertThat(report.total()).isEqualTo(3);
        assertThat(report.okCount()).isEqualTo(3);
        assertThat(report.failureCount()).isZero();
        assertThat(mock.chatRequests()).isEqualTo(3);
        assertThat(report.verdict()).contains("все 3 запросов выполнены");
        assertThat(report.results()).allSatisfy(result -> {
            assertThat(result.ok()).isTrue();
            assertThat(result.reply()).isEqualTo(Day26MockOllama.CYRILLIC_REPLY);
            assertThat(result.model()).isEqualTo(Day26Properties.DEFAULT_MODEL);
            assertThat(result.promptTokens()).isPositive();
            assertThat(result.outputTokens()).isPositive();
        });
        assertThat(report.results()).extracting(Day26TaskResult::id)
                .containsExactly("simple", "medium", "complex");
        assertThat(report.totalLatencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void runFailsGracefullyWhenServerIsStopped() {
        mock.close();

        Day26RunReport report = service.run();

        assertThat(report.total()).isEqualTo(3);
        assertThat(report.okCount()).isZero();
        assertThat(report.failureCount()).isEqualTo(3);
        assertThat(report.verdict()).contains("запустите локальный LLM");
        assertThat(report.results()).allSatisfy(result -> {
            assertThat(result.ok()).isFalse();
            assertThat(result.error()).contains("недоступен");
        });
    }

    @Test
    void runMarksSingleTaskFailureWithoutStoppingOthers() {
        mock.chatJson("{\"error\":\"context length exceeded\"}");
        mock.chatStatus(400);

        Day26RunReport report = service.run();

        assertThat(report.okCount()).isZero();
        assertThat(report.failureCount()).isEqualTo(3);
        assertThat(report.results()).allSatisfy(result ->
                assertThat(result.error()).contains("400"));
        assertThat(report.verdict()).contains("ни один из 3 запросов не выполнен");
    }

    @Test
    void askReturnsAnswerForSingleQuestion() {
        Day26Answer answer = service.ask("Сколько будет 17*23?");

        assertThat(answer.reply()).isEqualTo(Day26MockOllama.CYRILLIC_REPLY);
        assertThat(answer.prompt()).isEqualTo("Сколько будет 17*23?");
        assertThat(answer.tokensPerSecond()).isGreaterThan(0);
    }

    @Test
    void askRejectsBlankPrompt() {
        assertThatThrownBy(() -> service.ask("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("пустым");
    }
}
