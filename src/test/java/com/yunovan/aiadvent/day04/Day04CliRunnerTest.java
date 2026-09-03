package com.yunovan.aiadvent.day04;

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

class Day04CliRunnerTest {

    @Test
    void doesNothingUnlessDayIsFour() {
        Day04TemperatureService service = mock(Day04TemperatureService.class);
        Day04CliRunner runner = new Day04CliRunner(service, mock(ApplicationContext.class));

        runner.run(new DefaultApplicationArguments("--prompt=Hello"));

        verifyNoInteractions(service);
    }

    @Test
    void printsThreeTemperaturesForDayFour() {
        Day04TemperatureService service = mock(Day04TemperatureService.class);
        when(service.compare("Hello"))
                .thenReturn(new TemperatureResponse(
                        "Hello",
                        "openai/gpt-4o-mini",
                        List.of(
                                TemperatureSample.from(0.0, "zero", "stop"),
                                TemperatureSample.from(0.7, "mid", "stop"),
                                TemperatureSample.from(1.2, "high", "stop")),
                        Day04Temperatures.CONCLUSIONS));
        Day04CliRunner runner = new Day04CliRunner(service, mock(ApplicationContext.class));

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer));
        try {
            runner.run(new DefaultApplicationArguments("--day=4", "--prompt=Hello"));
        } finally {
            System.setOut(original);
        }

        String out = buffer.toString();
        assertThat(out).contains("temperature=0.0");
        assertThat(out).contains("zero");
        assertThat(out).contains("mid");
        assertThat(out).contains("high");
        assertThat(out).contains("CONCLUSIONS");
    }
}
