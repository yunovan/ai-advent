package com.yunovan.aiadvent.day03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day03CliRunnerTest {

    @Test
    void doesNothingUnlessDayIsThree() {
        Day03ReasoningService service = mock(Day03ReasoningService.class);
        Day03CliRunner runner = new Day03CliRunner(service, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--prompt=Hello"));

        verifyNoInteractions(service);
    }

    @Test
    void printsFourMethodsForDayThree() {
        Day03ReasoningService service = mock(Day03ReasoningService.class);
        when(service.solve("Hello"))
                .thenReturn(new ReasoningResponse(
                        "Hello",
                        "openai/gpt-4o-mini",
                        new MethodResult("direct", "Прямой ответ", "Hello", null, "direct-answer", "stop"),
                        new MethodResult("step-by-step", "Решай пошагово", "Hello+", null, "step-answer", "stop"),
                        new MethodResult(
                                "meta-prompt", "Сначала промпт", "used", "generated-prompt-text", "meta-answer", "stop"),
                        new MethodResult("experts", "Группа экспертов", "experts", null, "experts-answer", "stop")));
        Day03CliRunner runner = new Day03CliRunner(service, mock(ApplicationContext.class));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.run(new DefaultApplicationArguments("--day=3", "--prompt=Hello"));
        } finally {
            System.setOut(original);
        }

        String out = buffer.toString();
        assertThat(out).contains("direct-answer");
        assertThat(out).contains("step-answer");
        assertThat(out).contains("generated-prompt-text");
        assertThat(out).contains("meta-answer");
        assertThat(out).contains("experts-answer");
    }

    @Test
    void usesDefaultTaskWhenPromptMissing() {
        Day03ReasoningService service = mock(Day03ReasoningService.class);
        when(service.solve(Day03Prompts.DEFAULT_TASK))
                .thenReturn(new ReasoningResponse(
                        Day03Prompts.DEFAULT_TASK,
                        "openai/gpt-4o-mini",
                        new MethodResult("direct", "t", "p", null, "ok", "stop"),
                        new MethodResult("step-by-step", "t", "p", null, "ok", "stop"),
                        new MethodResult("meta-prompt", "t", "p", "g", "ok", "stop"),
                        new MethodResult("experts", "t", "p", null, "ok", "stop")));
        Day03CliRunner runner = new Day03CliRunner(service, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--day=3"));

        org.mockito.Mockito.verify(service).solve(Day03Prompts.DEFAULT_TASK);
    }
}
