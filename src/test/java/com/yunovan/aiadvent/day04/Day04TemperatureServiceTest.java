package com.yunovan.aiadvent.day04;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
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
class Day04TemperatureServiceTest {

    private static final String PROMPT = Day04Temperatures.DEFAULT_PROMPT;

    @Mock
    private LlmClient llmClient;

    @Mock
    private LlmProperties properties;

    @InjectMocks
    private Day04TemperatureService service;

    @Test
    void compareSendsSamePromptAtThreeTemperatures() {
        when(properties.model()).thenReturn("openai/gpt-4o-mini");
        when(llmClient.complete(CompletionCommand.withTemperature(PROMPT, 0.0)))
                .thenReturn(new LlmReply("Париж. Классический слоган.", "stop"));
        when(llmClient.complete(CompletionCommand.withTemperature(PROMPT, 0.7)))
                .thenReturn(new LlmReply("Париж. Слоган с лёгкой игрой слов.", "stop"));
        when(llmClient.complete(CompletionCommand.withTemperature(PROMPT, 1.2)))
                .thenReturn(new LlmReply("Париж, наверное. Слоган совсем фантастический и длинный.", "stop"));

        TemperatureResponse result = service.compare(PROMPT);

        assertThat(result.samples()).hasSize(3);
        assertThat(result.samples().get(0).temperature()).isEqualTo(0.0);
        assertThat(result.samples().get(1).temperature()).isEqualTo(0.7);
        assertThat(result.samples().get(2).temperature()).isEqualTo(1.2);
        assertThat(result.samples().get(0).content()).isNotEqualTo(result.samples().get(2).content());
        assertThat(result.samples().get(0).bestFor()).contains("Факты");
        assertThat(result.samples().get(2).bestFor()).contains("Идеи");
        assertThat(result.conclusions()).contains("0.0").contains("1.2");

        ArgumentCaptor<CompletionCommand> captor = ArgumentCaptor.forClass(CompletionCommand.class);
        verify(llmClient, times(3)).complete(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CompletionCommand::temperature)
                .containsExactly(0.0, 0.7, 1.2);
        assertThat(captor.getAllValues())
                .extracting(CompletionCommand::prompt)
                .containsOnly(PROMPT);
    }

    @Test
    void compareRejectsBlankPrompt() {
        assertThatThrownBy(() -> service.compare("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
