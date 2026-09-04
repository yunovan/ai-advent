package com.yunovan.aiadvent.day01;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.llm.LlmClient;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day01CliRunnerTest {

    @Test
    void doesNothingWithoutPrompt() {
        LlmClient llmClient = mock(LlmClient.class);
        ApplicationContext context = mock(ApplicationContext.class);
        Day01CliRunner runner = new Day01CliRunner(llmClient, context);

        runner.run(new DefaultApplicationArguments());

        verifyNoInteractions(llmClient);
    }

    @Test
    void skipsWhenDayIsTwo() {
        LlmClient llmClient = mock(LlmClient.class);
        Day01CliRunner runner = new Day01CliRunner(llmClient, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--day=2", "--prompt=Hello"));

        verifyNoInteractions(llmClient);
    }

    @Test
    void skipsWhenDayIsThree() {
        LlmClient llmClient = mock(LlmClient.class);
        Day01CliRunner runner = new Day01CliRunner(llmClient, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--day=3", "--prompt=Hello"));

        verifyNoInteractions(llmClient);
    }

    @Test
    void skipsWhenDayIsFour() {
        LlmClient llmClient = mock(LlmClient.class);
        Day01CliRunner runner = new Day01CliRunner(llmClient, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--day=4", "--prompt=Hello"));

        verifyNoInteractions(llmClient);
    }

    @Test
    void skipsWhenDayIsFive() {
        LlmClient llmClient = mock(LlmClient.class);
        Day01CliRunner runner = new Day01CliRunner(llmClient, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--day=5", "--prompt=Hello"));

        verifyNoInteractions(llmClient);
    }

    @Test
    void printsLlmResponseForPromptOption() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.complete("Hello from CLI")).thenReturn("CLI answer");
        Day01CliRunner runner = new Day01CliRunner(llmClient, mock(ApplicationContext.class));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.run(new DefaultApplicationArguments("--prompt=Hello from CLI"));
        } finally {
            System.setOut(original);
        }

        assertThat(buffer.toString()).contains("CLI answer");
        assertThat(buffer.toString()).contains("=== LLM response ===");
    }
}
