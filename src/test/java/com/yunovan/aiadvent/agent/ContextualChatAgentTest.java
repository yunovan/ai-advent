package com.yunovan.aiadvent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.agent.store.FileConversationStore;
import com.yunovan.aiadvent.day07.Day7Properties;
import com.yunovan.aiadvent.llm.ChatCompletionRequest;
import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ContextualChatAgentTest {

    @TempDir
    Path tempDir;

    @Mock
    private LlmClient llmClient;

    @Test
    void askStartsWithSystemPromptAndPersistsMessages() {
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Привет, Ася!"));
        ContextualChatAgent agent = newAgent();

        ConversationReply reply = agent.ask("default", "Привет, меня зовут Ася");

        assertThat(reply.sessionId()).isEqualTo("default");
        assertThat(reply.messageCount()).isEqualTo(3);
        assertThat(reply.messages()).extracting(ConversationMessage::role)
                .containsExactly("system", "user", "assistant");
        assertThat(reply.messages().get(1).content()).isEqualTo("Привет, меня зовут Ася");

        assertThat(store().load("default").messages()).extracting(ConversationMessage::role)
                .containsExactly("system", "user", "assistant");
    }

    @Test
    void newAgentOnSameStoreRemembersPreviousConversation() {
        ContextualChatAgent first = newAgent();
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("Как тебя зовут, Ася?"));
        first.ask("default", "Привет, меня зовут Ася");

        ContextualChatAgent restarted = newAgent();
        when(llmClient.complete(any(CompletionCommand.class), any()))
                .thenReturn(reply("Меня зовут Ася! Ты сам это сказал."));

        ConversationReply reply = restarted.ask("default", "Как меня зовут?");

        assertThat(reply.messageCount()).isEqualTo(5);
        assertThat(reply.messages()).extracting(ConversationMessage::content)
                .contains("Привет, меня зовут Ася", "Как тебя зовут, Ася?", "Как меня зовут?");

        ArgumentCaptor<List<ChatCompletionRequest.Message>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        verify(llmClient, org.mockito.Mockito.times(2)).complete(any(CompletionCommand.class), messagesCaptor.capture());
        List<ChatCompletionRequest.Message> lastCall = messagesCaptor.getAllValues().get(1);
        assertThat(lastCall).hasSize(4);
        assertThat(lastCall.getFirst().role()).isEqualTo("system");
        assertThat(lastCall.get(2).content()).isEqualTo("Как тебя зовут, Ася?");
    }

    @Test
    void resetClearsHistory() {
        ContextualChatAgent agent = newAgent();
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("hi"));
        agent.ask("default", "первое сообщение");

        agent.reset("default");

        assertThat(store().load("default").messages()).isEmpty();
    }

    @Test
    void askRejectsBlankRequest() {
        assertThatThrownBy(() -> newAgent().ask("default", "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private FileConversationStore store() {
        return new FileConversationStore(
                JsonMapper.builder().build(),
                new Day7Properties(tempDir.toString()));
    }

    private ContextualChatAgent newAgent() {
        return new ContextualChatAgent(
                llmClient,
                new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"),
                store());
    }

    @Test
    void historyGrowsWithoutLimitAcrossManyAsks() {
        ContextualChatAgent agent = newAgent();
        when(llmClient.complete(any(CompletionCommand.class), any())).thenReturn(reply("и снова привет"));
        for (int i = 1; i <= 5; i++) {
            agent.ask("default", "вопрос номер " + i);
        }

        ConversationReply reply = agent.ask("default", "вопрос номер 6");

        assertThat(reply.messageCount()).isEqualTo(1 + 2 * 6);
        assertThat(reply.messages()).extracting(ConversationMessage::content)
                .contains("вопрос номер 1", "вопрос номер 2", "вопрос номер 6");
        assertThat(store().load("default").messages()).hasSize(1 + 2 * 6);
    }

    private static LlmReply reply(String content) {
        return new LlmReply(content, "stop", 5, 7, 12, BigDecimal.ZERO, 100L);
    }
}