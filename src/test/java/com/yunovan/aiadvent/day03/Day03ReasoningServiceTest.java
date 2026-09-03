package com.yunovan.aiadvent.day03;

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
class Day03ReasoningServiceTest {

    private static final String TASK = Day03Prompts.DEFAULT_TASK;

    @Mock
    private LlmClient llmClient;

    @Mock
    private LlmProperties properties;

    @InjectMocks
    private Day03ReasoningService service;

    @Test
    void solveRunsFourMethodsIncludingGeneratedPrompt() {
        when(properties.model()).thenReturn("openai/gpt-4o-mini");
        when(llmClient.complete(CompletionCommand.unconstrained(TASK)))
                .thenReturn(new LlmReply("10 рублей", "stop"));
        when(llmClient.complete(CompletionCommand.unconstrained(Day03Prompts.stepByStep(TASK))))
                .thenReturn(new LlmReply("По шагам: мяч стоит 5 рублей.", "stop"));
        when(llmClient.complete(CompletionCommand.unconstrained(Day03Prompts.metaPromptRequest(TASK))))
                .thenReturn(new LlmReply("Реши через уравнения. Проверь ответ подстановкой.", "stop"));
        when(llmClient.complete(CompletionCommand.unconstrained(
                        Day03Prompts.applyGeneratedPrompt(
                                "Реши через уравнения. Проверь ответ подстановкой.", TASK))))
                .thenReturn(new LlmReply("Мяч стоит 5 рублей.", "stop"));
        when(llmClient.complete(CompletionCommand.unconstrained(Day03Prompts.experts(TASK))))
                .thenReturn(new LlmReply("### Аналитик\n5\n### Инженер\n5\n### Критик\n5 рублей", "stop"));

        ReasoningResponse result = service.solve(TASK);

        assertThat(result.task()).isEqualTo(TASK);
        assertThat(result.direct().content()).isEqualTo("10 рублей");
        assertThat(result.stepByStep().content()).contains("5 рублей");
        assertThat(result.metaPrompt().generatedPrompt()).contains("уравнения");
        assertThat(result.metaPrompt().content()).isEqualTo("Мяч стоит 5 рублей.");
        assertThat(result.experts().content()).contains("### Критик");
        assertThat(result.direct().content()).isNotEqualTo(result.stepByStep().content());

        ArgumentCaptor<CompletionCommand> captor = ArgumentCaptor.forClass(CompletionCommand.class);
        verify(llmClient, times(5)).complete(captor.capture());
        assertThat(captor.getAllValues().get(0).prompt()).isEqualTo(TASK);
        assertThat(captor.getAllValues().get(1).prompt()).contains("Решай пошагово");
        assertThat(captor.getAllValues().get(2).prompt()).contains("Составь промпт");
        assertThat(captor.getAllValues().get(4).prompt()).contains("Аналитик");
        assertThat(captor.getAllValues().get(4).prompt()).contains("Инженер");
        assertThat(captor.getAllValues().get(4).prompt()).contains("Критик");
    }

    @Test
    void solveRejectsBlankTask() {
        assertThatThrownBy(() -> service.solve("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
