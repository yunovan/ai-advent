package com.yunovan.aiadvent.day02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day02CliRunnerTest {

    @Test
    void doesNothingUnlessDayIsTwo() {
        Day02CompareService service = mock(Day02CompareService.class);
        Day02CliRunner runner = new Day02CliRunner(service, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--prompt=Hello"));

        verifyNoInteractions(service);
    }

    @Test
    void printsBothAnswersForDayTwo() {
        Day02CompareService service = mock(Day02CompareService.class);
        when(service.compare("Hello"))
                .thenReturn(new CompareResponse(
                        "Hello",
                        "openai/gpt-4o-mini",
                        new CompareSample("long answer here", "stop", 16, 3),
                        new CompareResponse.ConstrainedSample(
                                "1. Short.",
                                "stop",
                                9,
                                2,
                                "format",
                                80,
                                List.of("<<<END>>>"))));
        Day02CliRunner runner = new Day02CliRunner(service, mock(ApplicationContext.class));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.run(new DefaultApplicationArguments("--day=2", "--prompt=Hello"));
        } finally {
            System.setOut(original);
        }

        assertThat(buffer.toString()).contains("WITHOUT CONSTRAINTS");
        assertThat(buffer.toString()).contains("long answer here");
        assertThat(buffer.toString()).contains("WITH CONSTRAINTS");
        assertThat(buffer.toString()).contains("1. Short.");
    }
}
