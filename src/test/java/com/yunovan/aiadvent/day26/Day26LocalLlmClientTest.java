package com.yunovan.aiadvent.day26;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day26LocalLlmClientTest {

    private Day26MockOllama mock;
    private Day26LocalLlmClient client;

    @BeforeEach
    void setUp() {
        mock = Day26MockOllama.start();
        client = new Day26LocalLlmClient(org.springframework.web.client.RestClient.builder(),
                new Day26Properties(mock.endpoint(), null, null, null, null, null));
    }

    @AfterEach
    void tearDown() {
        mock.close();
    }

    @Test
    void versionReturnsServerVersion() {
        assertThat(client.version()).isEqualTo(Day26MockOllama.VERSION);
    }

    @Test
    void modelsReturnInstalledTagsWithDetails() {
        assertThat(client.models())
                .hasSize(1)
                .first()
                .satisfies(model -> {
                    assertThat(model.name()).isEqualTo(Day26MockOllama.MODEL_TAG);
                    assertThat(model.sizeBytes()).isEqualTo(1_998_578_976L);
                    assertThat(model.parameterSize()).isEqualTo("3.09B");
                    assertThat(model.quantizationLevel()).isEqualTo("Q4_K_M");
                });
    }

    @Test
    void chatReturnsCyrillicReplyWithTokenMetrics() {
        Day26Answer answer = client.chat("Привет! Кто ты такой?");

        assertThat(answer.reply()).isEqualTo(Day26MockOllama.CYRILLIC_REPLY);
        assertThat(answer.model()).isEqualTo(Day26Properties.DEFAULT_MODEL);
        assertThat(answer.endpoint()).isEqualTo(mock.endpoint());
        assertThat(answer.promptTokens()).isEqualTo(45);
        assertThat(answer.outputTokens()).isEqualTo(25);
        assertThat(answer.tokensPerSecond()).isGreaterThan(0);
        assertThat(answer.latencyMs()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void chatSendsUtf8PromptAndSystemMessage() {
        client.chat("Отвечай коротко.", "Что такое эмбеддинги?");

        String body = mock.lastChatBody();
        assertThat(body).contains("Что такое эмбеддинги?");
        assertThat(body).contains("Отвечай коротко.");
        assertThat(body).contains("\"stream\":false");
        assertThat(body).contains(Day26MockOllama.MODEL_TAG);
        assertThat(body).doesNotContain("\\u0427");
    }

    @Test
    void chatSendsConfiguredTemperatureAndTokenLimit() {
        client = new Day26LocalLlmClient(org.springframework.web.client.RestClient.builder(),
                new Day26Properties(mock.endpoint(), null, null, null, 0.7, 128));

        client.chat("вопрос");

        assertThat(mock.lastChatBody()).contains("\"temperature\":0.7").contains("\"num_predict\":128");
    }

    @Test
    void httpErrorFromServerBecomesDay26Exception() {
        mock.chatStatus(404);
        mock.chatJson("{\"error\":\"model 'missing' not found\"}");

        assertThatThrownBy(() -> client.chat("вопрос"))
                .isInstanceOf(Day26LlmException.class)
                .hasMessageContaining("404")
                .hasMessageContaining("model 'missing' not found");
    }

    @Test
    void unreachableEndpointBecomesDay26Exception() {
        Day26LocalLlmClient offline = new Day26LocalLlmClient(
                org.springframework.web.client.RestClient.builder(),
                new Day26Properties(Day26MockOllama.closedEndpoint(), null, null, null, null, null));

        assertThatThrownBy(() -> offline.chat("вопрос"))
                .isInstanceOf(Day26LlmException.class)
                .hasMessageContaining("недоступен");
    }

    @Test
    void blankPromptIsRejectedBeforeHttpCall() {
        assertThatThrownBy(() -> client.chat("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("пустым");
        assertThat(mock.chatRequests()).isZero();
    }

    @Test
    void emptyModelTagsAreReportedAsEmptyList() {
        mock.tagsJson("{\"models\":[]}");

        assertThat(client.models()).isEmpty();
    }
}
