package com.yunovan.aiadvent.day30;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day29.Day29ModelReport;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;

class Day30CliRunnerTest {

    private final Day30PrivateLlmService service = mock(Day30PrivateLlmService.class);
    private final ApplicationContext context = mock(ApplicationContext.class);
    private final Day30CliRunner runner = new Day30CliRunner(service, context);

    private String run(String... args) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            runner.run(new DefaultApplicationArguments(args));
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private static Day29ModelReport report() {
        return new Day29ModelReport("http://localhost:11434", "qwen2.5:3b", true, true,
                "0.35.1", "gguf", "3.1B", "Q4_K_M", 3_085_938_688L, 32_768L,
                1_998_578_976L, 2_047_774_554L, "");
    }

    private static Day30HealthResponse health() {
        return new Day30HealthResponse("ok", "http://localhost:8080", "localhost", 8080,
                List.of("http://localhost:8080", "http://192.168.1.10:8080"), report(),
                new Day30Limits(12, 6000, 10, 4, 300, 32_768L, false),
                new Day30Stats(3, 2, 1, 0, 0, 1, 2, 1, 333.0, 8.5));
    }

    private static Day30ChatResponse chat() {
        return new Day30ChatResponse("sess-1", 1, "Я Qwen, языковая модель от Alibaba Cloud.",
                2, 40, false, 1234L, 45, 25, 8.5, 9, "qwen2.5:3b");
    }

    private static Day30StressResponse stress() {
        return new Day30StressResponse(2, 2, 2, 0, 0, 700L, 2.9, 300.0, 250L, 350L,
                List.of(new Day30StressItem(1, true, false, 250L, 10, ""),
                        new Day30StressItem(2, true, false, 350L, 12, "")),
                "Сервис выдержал нагрузку без потерь.");
    }

    @Test
    void ignoresOtherDays() {
        String output = run("--day=29");

        assertThat(output).isEmpty();
        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsServiceStateAndLimits() {
        when(service.health()).thenReturn(health());

        String output = run("--day=30", "--check");

        assertThat(output).contains("Приватный AI-сервис");
        assertThat(output).contains("Статус: ok");
        assertThat(output).contains("http://localhost:8080");
        assertThat(output).contains("http://192.168.1.10:8080");
        assertThat(output).contains("Q4_K_M");
        assertThat(output).contains("rate limit 10/мин");
        assertThat(output).contains("Ключ доступа: не требуется");
        assertThat(output).contains("отклонено (лимит 1");
    }

    @Test
    void chatPrintsReplyAndMetrics() {
        when(service.chat(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(chat());

        String output = run("--day=30", "--chat=Кто ты?");

        assertThat(output).contains("Чат приватного сервиса");
        assertThat(output).contains("Я Qwen, языковая модель от Alibaba Cloud.");
        assertThat(output).contains("1234 мс");
        assertThat(output).contains("остаток лимита: 9");
    }

    @Test
    void stressPrintsVerdict() {
        when(service.stress(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(stress());

        String output = run("--day=30", "--stress");

        assertThat(output).contains("Проверка стабильности");
        assertThat(output).contains("Успешно: 2");
        assertThat(output).contains("Вердикт: Сервис выдержал нагрузку без потерь.");
    }

    @Test
    void helpPrintsUsageWhenNoActionGiven() {
        String output = run("--day=30");

        assertThat(output).contains("--check");
        assertThat(output).contains("--chat");
        assertThat(output).contains("--stress");
        verifyNoInteractions(service);
    }

    @Test
    void checkPrintsErrorWhenServerUnavailable() {
        when(service.health()).thenThrow(new Day26LlmException("Локальный LLM недоступен"));

        String output = run("--day=30", "--check");

        assertThat(output).contains("ОШИБКА:");
        assertThat(output).contains("Локальный LLM недоступен");
    }
}
