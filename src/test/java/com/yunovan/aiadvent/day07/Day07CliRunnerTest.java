package com.yunovan.aiadvent.day07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.agent.ConversationMessage;
import com.yunovan.aiadvent.agent.ConversationReply;
import com.yunovan.aiadvent.agent.ConversationalAgent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day07CliRunnerTest {

    @Test
    void doesNothingUnlessDayIsSeven() {
        ConversationalAgent agent = mock(ConversationalAgent.class);
        Day07CliRunner runner = new Day07CliRunner(agent, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--prompt=Hello"));

        verifyNoInteractions(agent);
    }

    @Test
    void doesNothingWithoutPromptForDaySeven() {
        ConversationalAgent agent = mock(ConversationalAgent.class);
        Day07CliRunner runner = new Day07CliRunner(agent, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--day=7"));

        verifyNoInteractions(agent);
    }

    @Test
    void printsFullConversationForDaySeven() {
        ConversationalAgent agent = mock(ConversationalAgent.class);
        when(agent.ask("default", "Как меня зовут?"))
                .thenReturn(new ConversationReply(
                        "default",
                        "Ася!",
                        "gpt-4o-mini",
                        5,
                        5,
                        5,
                        10,
                        null,
                        300L,
                        List.of(
                                ConversationMessage.system("sys"),
                                ConversationMessage.user("Привет, меня зовут Ася"),
                                ConversationMessage.assistant("Приятно познакомиться, Ася!"),
                                ConversationMessage.user("Как меня зовут?"),
                                ConversationMessage.assistant("Ася!"))));
        Day07CliRunner runner = new Day07CliRunner(agent, mock(ApplicationContext.class));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.run(new DefaultApplicationArguments("--day=7", "--prompt=Как меня зовут?"));
        } finally {
            System.setOut(original);
        }

        String out = buffer.toString();
        assertThat(out).contains("Как меня зовут?");
        assertThat(out).contains("Приятно познакомиться, Ася!");
        assertThat(out).contains("Ася!");
        assertThat(out).contains("5 сообщений");
    }

    @Test
    void resetClearsSessionHistory() {
        ConversationalAgent agent = mock(ConversationalAgent.class);
        Day07CliRunner runner = new Day07CliRunner(agent, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--day=7", "--reset"));

        verify(agent).reset("default");
    }
}