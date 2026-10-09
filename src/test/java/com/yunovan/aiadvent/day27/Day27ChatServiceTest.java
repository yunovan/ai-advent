package com.yunovan.aiadvent.day27;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day26.Day26MockOllama;
import com.yunovan.aiadvent.day26.Day26Properties;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day27ChatServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Day26MockOllama mock;
    private Day27ChatSessionStore store;
    private Day27ChatService service;

    @BeforeEach
    void setUp() {
        mock = Day26MockOllama.start();
        store = new Day27ChatSessionStore(Path.of("build/day27-tests-chat-service"), 5);
        store.clear();
        service = new Day27ChatService(
                new Day26LocalLlmClient(org.springframework.web.client.RestClient.builder(),
                        new Day26Properties(mock.endpoint(), null, null, null, null, null)),
                new Day26Properties(mock.endpoint(), null, null, null, null, null),
                new Day27Properties(null, 4, 5, null),
                store);
    }

    @AfterEach
    void tearDown() {
        mock.close();
    }

    @Test
    void healthReportsRunningServerWithoutCloudModels() {
        Day27HealthResponse health = service.health();

        assertThat(health.available()).isTrue();
        assertThat(health.endpoint()).isEqualTo(mock.endpoint());
        assertThat(health.version()).isEqualTo(Day26MockOllama.VERSION);
        assertThat(health.modelInstalled()).isTrue();
        assertThat(health.usesCloud()).isFalse();
        assertThat(health.historyLimit()).isEqualTo(4);
        assertThat(health.maxSessions()).isEqualTo(5);
        assertThat(health.sessions()).isZero();
        assertThat(health.error()).isEmpty();
    }

    @Test
    void healthReportsStoppedServerInsteadOfThrowing() {
        mock.close();

        Day27HealthResponse health = service.health();

        assertThat(health.available()).isFalse();
        assertThat(health.version()).isEmpty();
        assertThat(health.error()).contains("недоступен");
        assertThat(health.usesCloud()).isFalse();
    }

    @Test
    void firstTurnSendsSystemPromptAndUserMessageInUtf8() throws Exception {
        Day27ChatTurn turn = service.chat("web", "Привет! Кто ты такой?");

        assertThat(turn.sessionId()).isEqualTo("web");
        assertThat(turn.turn()).isEqualTo(1);
        assertThat(turn.reply()).isEqualTo(Day26MockOllama.CYRILLIC_REPLY);
        assertThat(turn.model()).isEqualTo(Day26Properties.DEFAULT_MODEL);

        JsonNode messages = MAPPER.readTree(mock.lastChatBody()).get("messages");
        assertThat(messages).hasSize(2);
        assertThat(messages.findValuesAsText("role")).containsExactly("system", "user");
        assertThat(messages.get(0).get("content").asText())
                .contains("Локальный ассистент");
        assertThat(messages.get(1).get("content").asText()).isEqualTo("Привет! Кто ты такой?");
        assertThat(mock.lastChatBody()).doesNotContain("\\u041f");
    }

    @Test
    void secondTurnSendsFullDialogHistoryToLocalModel() throws Exception {
        service.chat("web", "Меня зовут Аня");
        Day27ChatTurn second = service.chat("web", "Как меня зовут?");

        assertThat(second.turn()).isEqualTo(2);

        JsonNode messages = MAPPER.readTree(mock.lastChatBody()).get("messages");
        assertThat(messages).hasSize(4);
        assertThat(messages.findValuesAsText("role"))
                .containsExactly("system", "user", "assistant", "user");
        String body = mock.lastChatBody();
        assertThat(body).contains("Меня зовут Аня");
        assertThat(body).contains(Day26MockOllama.CYRILLIC_REPLY);
        assertThat(body).contains("Как меня зовут?");
    }

    @Test
    void historyKeepsBothTurnsWithAssistantMetrics() {
        service.chat("web", "Первое сообщение");
        service.chat("web", "Второе сообщение");

        List<Day27Message> history = service.history("web");

        assertThat(history).hasSize(4);
        assertThat(history).extracting(Day27Message::role)
                .containsExactly("user", "assistant", "user", "assistant");
        assertThat(history).extracting(Day27Message::turn)
                .containsExactly(1, 1, 2, 2);
        assertThat(history.getFirst().text()).isEqualTo("Первое сообщение");
        Day27Message reply = history.get(1);
        assertThat(reply.text()).isEqualTo(Day26MockOllama.CYRILLIC_REPLY);
        assertThat(reply.promptTokens()).isEqualTo(45);
        assertThat(reply.outputTokens()).isEqualTo(25);
        assertThat(reply.tokensPerSecond()).isGreaterThan(0);
    }

    @Test
    void customSystemPromptReachesTheModel() throws Exception {
        service = new Day27ChatService(
                new Day26LocalLlmClient(org.springframework.web.client.RestClient.builder(),
                        new Day26Properties(mock.endpoint(), null, null, null, null, null)),
                new Day26Properties(mock.endpoint(), null, null, null, null, null),
                new Day27Properties("Ты — корабельный компьютер HAL.", 4, 5, null),
                store);

        service.chat("web", "Ответь одной фразой");

        JsonNode messages = MAPPER.readTree(mock.lastChatBody()).get("messages");
        assertThat(messages.get(0).get("content").asText())
                .isEqualTo("Ты — корабельный компьютер HAL.");
    }

    @Test
    void blankMessageIsRejectedBeforeModelCall() {
        assertThatThrownBy(() -> service.chat("web", "   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Сообщение не может быть пустым");
        assertThat(mock.chatRequests()).isZero();
    }

    @Test
    void chatWhenServerStoppedThrowsDay26Exception() {
        mock.close();

        assertThatThrownBy(() -> service.chat("web", "Привет"))
                .isInstanceOf(Day26LlmException.class)
                .hasMessageContaining("недоступен");
    }

    @Test
    void historyIsTrimmedToLimitKeepingTurnPairs() {
        service.chat("web", "сообщение 1");
        service.chat("web", "сообщение 2");
        service.chat("web", "сообщение 3");

        List<Day27Message> history = service.history("web");

        assertThat(history).hasSize(4);
        assertThat(history.getFirst().text()).isEqualTo("сообщение 2");
        assertThat(history.getLast().text()).isEqualTo(Day26MockOllama.CYRILLIC_REPLY);
        assertThat(store.get("web").messages()).hasSize(4);
    }

    @Test
    void resetClearsSessionButKeepsOthers() {
        service.chat("web", "Привет");
        service.chat("cli", "Здравствуй");

        service.reset("web");

        assertThat(service.history("web")).isEmpty();
        assertThat(service.history("cli")).hasSize(2);
    }

    @Test
    void sessionsListShowsTurnsAndLastMessage() {
        service.chat("web", "Привет");
        service.chat("web", "Ещё вопрос");

        List<Day27SessionView> sessions = service.sessions();

        assertThat(sessions).hasSize(1);
        assertThat(sessions.getFirst().sessionId()).isEqualTo("web");
        assertThat(sessions.getFirst().turns()).isEqualTo(2);
        assertThat(sessions.getFirst().historySize()).isEqualTo(4);
        assertThat(sessions.getFirst().lastMessage()).isEqualTo(Day26MockOllama.CYRILLIC_REPLY);
    }

    @Test
    void historyForUnknownSessionIsEmpty() {
        assertThat(service.history("нет-такой")).isEmpty();
        assertThat(service.history(null)).isEmpty();
    }
}
