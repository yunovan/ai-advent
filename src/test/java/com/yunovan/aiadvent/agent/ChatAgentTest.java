package com.yunovan.aiadvent.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatAgentTest {

    @Mock
    private LlmClient llmClient;

    private ChatAgent agent;

    @BeforeEach
    void setUp() {
        agent = new ChatAgent(llmClient, new LlmProperties("key", "https://openrouter.ai/api/v1", "gpt-4o-mini"));
    }

    @Test
    void askEncapsulatesRequestAndReturnsAgentReply() {
        when(llmClient.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply(
                        "Я агент из AI Advent.", "stop", 12, 30, 42, new BigDecimal("0.00002"), 777L));

        AgentReply reply = agent.ask("Кто ты?");

        assertThat(reply.content()).isEqualTo("Я агент из AI Advent.");
        assertThat(reply.model()).isEqualTo("gpt-4o-mini");
        assertThat(reply.totalTokens()).isEqualTo(42);
        assertThat(reply.costUsd()).isEqualByComparingTo("0.00002");
        assertThat(reply.elapsedMs()).isEqualTo(777L);

        ArgumentCaptor<CompletionCommand> captor = ArgumentCaptor.forClass(CompletionCommand.class);
        verify(llmClient).complete(captor.capture());
        assertThat(captor.getValue().prompt()).isEqualTo("Кто ты?");
        assertThat(captor.getValue().systemPrompt()).isEqualTo(ChatAgent.SYSTEM_PROMPT);
    }

    @Test
    void askRejectsBlankRequest() {
        assertThatThrownBy(() -> agent.ask("   ")).isInstanceOf(IllegalArgumentException.class);
    }
}