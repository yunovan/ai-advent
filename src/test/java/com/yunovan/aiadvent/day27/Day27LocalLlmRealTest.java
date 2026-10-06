package com.yunovan.aiadvent.day27;

import static org.assertj.core.api.Assertions.assertThat;

import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class Day27LocalLlmRealTest {

    private static Day27ChatService service;

    @BeforeAll
    static void prepare() {
        String endpoint = System.getenv().getOrDefault("DAY26_ENDPOINT",
                Day26Properties.DEFAULT_ENDPOINT);
        Day26Properties llmProperties = new Day26Properties(endpoint, null, null, null, null, null);
        service = new Day27ChatService(
                new Day26LocalLlmClient(org.springframework.web.client.RestClient.builder(), llmProperties),
                llmProperties,
                new Day27Properties(null, 40, 5, null),
                new Day27ChatSessionStore(Path.of("build/day27-real-test"), 5));
        service.reset("real");

        Day27HealthResponse health = service.health();
        Assumptions.assumeTrue(health.available(),
                "Ollama не запущена на " + endpoint + " - тест пропущен");
        Assumptions.assumeTrue(health.modelInstalled(),
                "модель " + health.model() + " не установлена - тест пропущен");
    }

    @Test
    void healthDetectsLocalAssistantWithoutCloud() {
        Day27HealthResponse health = service.health();

        assertThat(health.available()).isTrue();
        assertThat(health.version()).isNotBlank();
        assertThat(health.modelInstalled()).isTrue();
        assertThat(health.usesCloud()).isFalse();
    }

    @Test
    void dialogRemembersEarlierTurns() {
        Day27ChatTurn first = service.chat("real", "Меня зовут Аня. Запомни это имя.");

        assertThat(first.turn()).isEqualTo(1);
        assertThat(first.reply()).isNotBlank();
        assertThat(first.historySize()).isEqualTo(2);

        Day27ChatTurn second = service.chat("real", "Как меня зовут? Ответь одним словом.");

        assertThat(second.turn()).isEqualTo(2);
        assertThat(second.reply()).contains("Аня");
        assertThat(second.historySize()).isEqualTo(4);
        assertThat(second.promptTokens()).isGreaterThan(first.promptTokens());

        assertThat(service.history("real"))
                .extracting(Day27Message::role)
                .containsExactly("user", "assistant", "user", "assistant");
    }
}
