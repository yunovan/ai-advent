package com.yunovan.aiadvent.day20;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmReply;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day20AgentServiceTest {

    private Day20Orchestrator orchestrator;
    private LlmClient llm;
    private Day20AgentService service;

    @BeforeEach
    void setUp() {
        orchestrator = mock(Day20Orchestrator.class);
        llm = mock(LlmClient.class);
        service = new Day20AgentService(orchestrator, llm);
    }

    private Day20FlowResponse flowResponse() {
        return new Day20FlowResponse("market-report", "Флоу", Map.of("query", "ноутбукам"),
                List.of(
                        new Day20FlowStepResult("market", "search", Map.of("query", "ноутбукам"),
                                true, "[{\"products\":2}]"),
                        new Day20FlowStepResult("scheduler", "scheduler_add_reminder",
                                Map.of("topic", "Отчёт сохранён"), true, "{\"id\":\"j-1\"}")),
                "Флоу «market-report» — шагов: 2, успешно: 2/2");
    }

    @Test
    void submitFlowIntentRunsCrossServerFlow() {
        when(orchestrator.runFlow(eq("market-report"), anyMap())).thenReturn(flowResponse());

        Day20AgentResponse response = service.submit("составь полный отчёт по ноутбукам и сохрани в файл csv");

        assertThat(response.intent()).isEqualTo("flow");
        assertThat(response.server()).isNull();
        assertThat(response.tool()).isEqualTo("market-report");
        assertThat(response.arguments().get("query")).isEqualTo("ноутбукам");
        assertThat(response.arguments().get("format")).isEqualTo("csv");
        assertThat(response.toolResult()).contains("Флоу «market-report»");
        verify(orchestrator).runFlow(eq("market-report"), anyMap());
    }

    @Test
    void submitSchedulerSummaryIntentRoutesToScheduler() {
        when(orchestrator.route(eq("scheduler_summary"), anyMap()))
                .thenReturn(new Day20CallResponse("scheduler", "scheduler_summary", Map.of(),
                        true, "Сводка по событиям: 12 событий, успешность 100%"));

        Day20AgentResponse response = service.submit("сводку по событиям планировщика");

        assertThat(response.intent()).isEqualTo("scheduler");
        assertThat(response.server()).isEqualTo("scheduler");
        assertThat(response.tool()).isEqualTo("scheduler_summary");
        assertThat(response.toolResult()).contains("Сводка по событиям");
    }

    @Test
    void submitSchedulerListIntentChoosesListTool() {
        when(orchestrator.route(eq("scheduler_list_jobs"), anyMap()))
                .thenReturn(new Day20CallResponse("scheduler", "scheduler_list_jobs", Map.of(),
                        true, "[\"j-100\"]"));

        Day20AgentResponse response = service.submit("покажи список джобов планировщика");

        assertThat(response.intent()).isEqualTo("scheduler");
        assertThat(response.tool()).isEqualTo("scheduler_list_jobs");
        assertThat(response.toolResult()).contains("j-100");
    }

    @Test
    void submitMarketSearchIntentRoutesToMarket() {
        when(orchestrator.callText(eq("market"), eq("search"), anyMap()))
                .thenReturn("{\"products\":[{\"title\":\"Наушники\"}]}");

        Day20AgentResponse response = service.submit("найди наушники");

        assertThat(response.intent()).isEqualTo("market");
        assertThat(response.server()).isEqualTo("market");
        assertThat(response.tool()).isEqualTo("search");
        assertThat(response.arguments().get("query")).isEqualTo("наушники");
        assertThat(response.toolResult()).contains("Наушники");
    }

    @Test
    void submitMarketCompareIntentChainsSearchThenSummarize() {
        when(orchestrator.callText(eq("market"), eq("search"), anyMap()))
                .thenReturn("{\"products\":[{\"title\":\"Смартфон A\"}]}");
        when(orchestrator.callText(eq("market"), eq("summarize"), anyMap()))
                .thenReturn("Сравнение по запросу «смартфоны» — товаров: 4");

        Day20AgentResponse response = service.submit("сравни смартфоны в таблицу");

        assertThat(response.intent()).isEqualTo("market");
        assertThat(response.server()).isEqualTo("market");
        assertThat(response.tool()).isEqualTo("summarize");
        assertThat(response.arguments().get("format")).isEqualTo("markdown");
        assertThat(response.arguments().get("query")).isEqualTo("смартфоны");
        assertThat(response.toolResult()).contains("Сравнение по запросу «смартфоны»");
        verify(orchestrator).callText(eq("market"), eq("search"), anyMap());
        verify(orchestrator).callText(eq("market"), eq("summarize"), anyMap());
    }

    @Test
    void phraseWithLlmReturnsFramedAnswer() {
        when(orchestrator.callText(eq("market"), eq("search"), anyMap()))
                .thenReturn("{\"products\":[]}");
        when(llm.complete(any(CompletionCommand.class))).thenReturn(new LlmReply("Нашёл 3 товара!", "stop"));

        Day20AgentResponse response = service.submit("найди смартфоны");

        assertThat(response.answer()).isEqualTo("Нашёл 3 товара!");
        verify(llm).complete(any(CompletionCommand.class));
    }

    @Test
    void phraseFallsBackToRawResultWhenLlmFails() {
        when(orchestrator.callText(eq("market"), eq("search"), anyMap()))
                .thenReturn("{\"products\":[]}");
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("нет ключа"));

        Day20AgentResponse response = service.submit("найди смартфоны");

        assertThat(response.answer()).contains("Результат инструмента").contains("market");
    }

    @Test
    void submitUnrecognizedPromptThrowsWithHint() {
        assertThatThrownBy(() -> service.submit("расскажи анекдот"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Примеры");
    }

    @Test
    void submitBlankPromptThrows() {
        assertThatThrownBy(() -> service.submit("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустым");
    }
}