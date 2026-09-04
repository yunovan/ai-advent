package com.yunovan.aiadvent.day05;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmReply;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Day05CompareServiceTest {

    private static final String PROMPT = Day5Models.DEFAULT_PROMPT;

    @Mock
    private LlmClient llmClient;

    private Day05CompareService service;

    @BeforeEach
    void setUp() {
        service = new Day05CompareService(
                llmClient, new Day5Properties(Day5Models.WEAK_MODEL, Day5Models.MEDIUM_MODEL, Day5Models.STRONG_MODEL));
    }

    @Test
    void compareSendsSamePromptToThreeModels() {
        when(llmClient.complete(CompletionCommand.withModel(PROMPT, Day5Models.WEAK_MODEL)))
                .thenReturn(new LlmReply("Коротко и с ошибкой 400.", "stop", 20, 30, 50, new BigDecimal("0.00001"), 120L));
        when(llmClient.complete(CompletionCommand.withModel(PROMPT, Day5Models.MEDIUM_MODEL)))
                .thenReturn(new LlmReply(
                        "Профиль крыла даёт подъёмную силу. 17×24=408.",
                        "stop",
                        20,
                        80,
                        100,
                        new BigDecimal("0.00008"),
                        400L));
        when(llmClient.complete(CompletionCommand.withModel(PROMPT, Day5Models.STRONG_MODEL)))
                .thenReturn(new LlmReply(
                        "Подробное объяснение Бернулли и угла атаки. 17 × 24 = 408.",
                        "stop",
                        20,
                        160,
                        180,
                        new BigDecimal("0.00040"),
                        900L));

        ModelComparisonResponse result = service.compare(PROMPT);

        assertThat(result.runs()).hasSize(3);
        assertThat(result.runs()).extracting(ModelRun::tier).containsExactly("weak", "medium", "strong");
        assertThat(result.runs())
                .extracting(ModelRun::model)
                .containsExactly(Day5Models.WEAK_MODEL, Day5Models.MEDIUM_MODEL, Day5Models.STRONG_MODEL);
        assertThat(result.runs().get(0).elapsedMs()).isEqualTo(120L);
        assertThat(result.runs().get(1).totalTokens()).isEqualTo(100);
        assertThat(result.runs().get(2).costUsd()).isEqualByComparingTo("0.00040");
        assertThat(result.runs().get(0).huggingFaceUrl()).contains("Llama-3.2-3B-Instruct");
        assertThat(result.runs().get(2).openRouterUrl()).contains("llama-3.3-70b-instruct");
        assertThat(result.conclusion()).contains("Быстрее всех").contains("3B").contains("70B");
        assertThat(result.links()).extracting(ModelLink::url).contains(
                Day5Models.MODELS_CATALOG_OPENROUTER, Day5Models.MODELS_CATALOG_HUGGINGFACE);

        ArgumentCaptor<CompletionCommand> captor = ArgumentCaptor.forClass(CompletionCommand.class);
        verify(llmClient, times(3)).complete(captor.capture());
        assertThat(captor.getAllValues()).extracting(CompletionCommand::prompt).containsOnly(PROMPT);
        assertThat(captor.getAllValues())
                .extracting(CompletionCommand::model)
                .containsExactly(Day5Models.WEAK_MODEL, Day5Models.MEDIUM_MODEL, Day5Models.STRONG_MODEL);
    }

    @Test
    void compareRejectsBlankPrompt() {
        assertThatThrownBy(() -> service.compare("  ")).isInstanceOf(IllegalArgumentException.class);
    }
}
