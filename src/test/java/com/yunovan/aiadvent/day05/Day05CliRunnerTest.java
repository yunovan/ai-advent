package com.yunovan.aiadvent.day05;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.llm.LlmReply;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day05CliRunnerTest {

    @Test
    void doesNothingUnlessDayIsFive() {
        Day05CompareService service = mock(Day05CompareService.class);
        Day05CliRunner runner = new Day05CliRunner(service, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--prompt=Hello"));

        verifyNoInteractions(service);
    }

    @Test
    void printsThreeModelsForDayFive() {
        Day05CompareService service = mock(Day05CompareService.class);
        when(service.compare("Hello"))
                .thenReturn(new ModelComparisonResponse(
                        "Hello",
                        List.of(
                                ModelRun.from(
                                        ModelTier.WEAK,
                                        Day5Models.WEAK_MODEL,
                                        new LlmReply("weak-answer", "stop", 1, 2, 3, BigDecimal.ZERO, 50L)),
                                ModelRun.from(
                                        ModelTier.MEDIUM,
                                        Day5Models.MEDIUM_MODEL,
                                        new LlmReply("medium-answer", "stop", 1, 4, 5, new BigDecimal("0.0001"), 80L)),
                                ModelRun.from(
                                        ModelTier.STRONG,
                                        Day5Models.STRONG_MODEL,
                                        new LlmReply("strong-answer", "stop", 1, 8, 9, new BigDecimal("0.001"), 200L))),
                        "сильная точнее",
                        List.of(new ModelLink("Каталог OpenRouter", Day5Models.MODELS_CATALOG_OPENROUTER))));
        Day05CliRunner runner = new Day05CliRunner(service, mock(ApplicationContext.class));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.run(new DefaultApplicationArguments("--day=5", "--prompt=Hello"));
        } finally {
            System.setOut(original);
        }

        String out = buffer.toString();
        assertThat(out).contains("weak-answer");
        assertThat(out).contains("medium-answer");
        assertThat(out).contains("strong-answer");
        assertThat(out).contains("CONCLUSION");
        assertThat(out).contains("openrouter.ai/models");
    }
}
