package com.yunovan.aiadvent.day07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.agent.ConversationMessage;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day07CliRunnerTest {

    @Test
    void doesNothingUnlessDayIsSeven() {
        Day07DialogService service = mock(Day07DialogService.class);
        Day07CliRunner runner = new Day07CliRunner(service, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--prompt=Hello"));

        verifyNoInteractions(service);
    }

    @Test
    void doesNothingWithoutCommandForDaySeven() {
        Day07DialogService service = mock(Day07DialogService.class);
        Day07CliRunner runner = new Day07CliRunner(service, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--day=7"));

        verifyNoInteractions(service);
    }

    @Test
    void startPrintsDialogIdAndMemory() {
        Day07DialogService service = mock(Day07DialogService.class);
        when(service.start()).thenReturn(new Day07StartResponse("d1", Instant.now(), List.of(), List.of()));
        Day07CliRunner runner = new Day07CliRunner(service, mock(ApplicationContext.class));

        String out = captureOut(() -> runner.run(new DefaultApplicationArguments("--day=7", "--start")));

        assertThat(out).contains("d1");
    }

    @Test
    void chatPrintsFullConversation() {
        Day07DialogService service = mock(Day07DialogService.class);
        when(service.chat("d1", "Как меня зовут?")).thenReturn(new Day07ChatResponse(
                "d1",
                "Как меня зовут?",
                "Ася!",
                "gpt-4o-mini",
                4,
                300L,
                List.of(
                        ConversationMessage.user("Привет, меня зовут Ася"),
                        ConversationMessage.assistant("Приятно познакомиться, Ася!"),
                        ConversationMessage.user("Как меня зовут?"),
                        ConversationMessage.assistant("Ася!")),
                List.of()));
        Day07CliRunner runner = new Day07CliRunner(service, mock(ApplicationContext.class));

        String out = captureOut(() -> runner.run(
                new DefaultApplicationArguments("--day=7", "--dialog=d1", "--prompt=Как меня зовут?")));

        assertThat(out)
                .contains("Как меня зовут?")
                .contains("Приятно познакомиться, Ася!")
                .contains("Ася!")
                .contains("4 сообщений");
    }

    @Test
    void finishPrintsSummary() {
        Day07DialogService service = mock(Day07DialogService.class);
        when(service.finish("d1")).thenReturn(new Day07FinishResponse("d1", Instant.now(), "Итог про небо", 2));
        Day07CliRunner runner = new Day07CliRunner(service, mock(ApplicationContext.class));

        String out = captureOut(() -> runner.run(
                new DefaultApplicationArguments("--day=7", "--dialog=d1", "--finish")));

        assertThat(out).contains("Итог про небо");
    }

    @Test
    void listPrintsFinishedDialogs() {
        Day07DialogService service = mock(Day07DialogService.class);
        when(service.dialogs()).thenReturn(List.of(new Day07DialogSummary(
                "d1", Instant.now(), Instant.now(), "итог", 2)));
        Day07CliRunner runner = new Day07CliRunner(service, mock(ApplicationContext.class));

        String out = captureOut(() -> runner.run(new DefaultApplicationArguments("--day=7", "--list")));

        assertThat(out).contains("итог");
    }

    private static String captureOut(Runnable runnable) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runnable.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString();
    }
}