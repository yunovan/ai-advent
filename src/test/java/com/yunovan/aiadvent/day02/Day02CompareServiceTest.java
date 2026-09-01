package com.yunovan.aiadvent.day02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmProperties;
import com.yunovan.aiadvent.llm.LlmReply;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Day02CompareServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private LlmProperties properties;

    @InjectMocks
    private Day02CompareService service;

    @Test
    void compareSendsSamePromptWithAndWithoutControls() {
        when(properties.model()).thenReturn("openai/gpt-4o-mini");
        when(llmClient.complete(CompletionCommand.unconstrained("Что такое ИИ?")))
                .thenReturn(new LlmReply(
                        "Искусственный интеллект — это длинный свободный рассказ про историю, обучение моделей, данные и области применения без какого-либо лимита.",
                        "stop"));
        when(llmClient.complete(new CompletionCommand(
                        "Что такое ИИ?",
                        Day02Constraints.SYSTEM_PROMPT,
                        Day02Constraints.MAX_TOKENS,
                        Day02Constraints.STOP)))
                .thenReturn(new LlmReply("1. Машины учатся.\n2. Помогают людям.\n3. Нужны в работе.", "stop"));

        CompareResponse result = service.compare("Что такое ИИ?");

        assertThat(result.prompt()).isEqualTo("Что такое ИИ?");
        assertThat(result.model()).isEqualTo("openai/gpt-4o-mini");
        assertThat(result.unconstrained().content()).contains("свободный рассказ");
        assertThat(result.unconstrained().words()).isGreaterThan(result.constrained().words());
        assertThat(result.constrained().maxTokens()).isEqualTo(80);
        assertThat(result.constrained().stop()).containsExactly("<<<END>>>");
        assertThat(result.constrained().formatInstruction()).contains("3 нумерованных пункта");

        ArgumentCaptor<CompletionCommand> captor = ArgumentCaptor.forClass(CompletionCommand.class);
        verify(llmClient, org.mockito.Mockito.times(2)).complete(captor.capture());
        assertThat(captor.getAllValues().get(0).systemPrompt()).isNull();
        assertThat(captor.getAllValues().get(0).maxTokens()).isNull();
        assertThat(captor.getAllValues().get(1).maxTokens()).isEqualTo(80);
        assertThat(captor.getAllValues().get(1).stop()).contains("<<<END>>>");
    }

    @Test
    void compareRejectsBlankPrompt() {
        assertThatThrownBy(() -> service.compare("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
