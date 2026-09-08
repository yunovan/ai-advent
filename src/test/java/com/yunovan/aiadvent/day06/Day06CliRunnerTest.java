package com.yunovan.aiadvent.day06;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.agent.Agent;
import com.yunovan.aiadvent.agent.AgentReply;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day06CliRunnerTest {

    @Test
    void doesNothingUnlessDayIsSix() {
        Agent agent = mock(Agent.class);
        Day06CliRunner runner = new Day06CliRunner(agent, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--prompt=Hello"));

        verifyNoInteractions(agent);
    }

    @Test
    void doesNothingWithoutPromptForDaySix() {
        Agent agent = mock(Agent.class);
        Day06CliRunner runner = new Day06CliRunner(agent, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--day=6"));

        verifyNoInteractions(agent);
    }

    @Test
    void printsAgentReplyForDaySix() {
        Agent agent = mock(Agent.class);
        when(agent.ask("Hello")).thenReturn(new AgentReply("AGENT ANSWER", "gpt-4o-mini", 1, 2, 3, null, 55L));
        Day06CliRunner runner = new Day06CliRunner(agent, mock(ApplicationContext.class));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.run(new DefaultApplicationArguments("--day=6", "--prompt=Hello"));
        } finally {
            System.setOut(original);
        }

        String out = buffer.toString();
        assertThat(out).contains("AGENT ANSWER");
        assertThat(out).contains("gpt-4o-mini");
        assertThat(out).contains("=== AGENT REPLY ===");
    }
}