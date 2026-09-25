package com.yunovan.aiadvent.day18;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.llm.CompletionCommand;
import com.yunovan.aiadvent.llm.LlmClient;
import com.yunovan.aiadvent.llm.LlmException;
import com.yunovan.aiadvent.llm.LlmReply;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Day18AgentServiceTest {

    @TempDir
    Path tempDir;

    private Day18McpServer server;
    private Day18SchedulerService scheduler;
    private LlmClient llm;
    private Day18AgentService service;

    @BeforeEach
    void setUp() {
        scheduler = new Day18SchedulerService(new Day18Store(tempDir),
                new Day18Properties(0, "/mcp", "ai-advent-scheduler-mcp", "0.1.0", "dir", 50L),
                new Day18HttpProbe());
        scheduler.start();
        server = new Day18McpServer(
                new Day18Properties(0, "/mcp", "ai-advent-scheduler-mcp", "0.1.0", "dir", 50L), scheduler);
        server.start();
        Day18McpClient client = new Day18McpClient(
                new Day18Properties(server.boundPort(), "/mcp", "ai-advent-scheduler-mcp", "0.1.0", "dir", 50L));
        llm = mock(LlmClient.class);
        service = new Day18AgentService(client, llm);
    }

    @AfterEach
    void tearDown() {
        server.stop();
        scheduler.stop();
    }

    @Test
    void healthReportsConnectedServerAndFiveTools() {
        Day18HealthResponse health = service.health();

        assertThat(health.connected()).isTrue();
        assertThat(health.serverName()).isEqualTo("ai-advent-scheduler-mcp");
        assertThat(health.serverVersion()).isEqualTo("0.1.0");
        assertThat(health.toolCount()).isEqualTo(5);
    }

    @Test
    void toolsReturnsTheFiveSchedulerTools() {
        List<Day18ToolInfo> tools = service.tools();

        assertThat(tools.stream().map(Day18ToolInfo::name)).containsExactlyInAnyOrder(
                "scheduler_add_reminder", "scheduler_add_collector", "scheduler_list_jobs",
                "scheduler_summary", "scheduler_run_now");
    }

    @Test
    void submitReminderParsesTopicAndDelay() {
        Day18AgentResponse response = service.submit("напомни через 2 секунды выпить чай");

        assertThat(response.tool()).isEqualTo("scheduler_add_reminder");
        assertThat(response.arguments().get("topic")).isEqualTo("выпить чай");
        assertThat(response.arguments().get("delaySeconds")).isEqualTo(2);
        assertThat(response.toolResult()).contains("reminder");
        assertThat(response.toolError()).isFalse();
        assertThat(response.answer()).contains("Результат инструмента:");
    }

    @Test
    void submitCollectorParsesFeedAndPeriod() {
        Day18AgentResponse response = service.submit("собирай данные каждые 5 секунд по events");

        assertThat(response.tool()).isEqualTo("scheduler_add_collector");
        assertThat(response.arguments().get("feed")).isEqualTo("events");
        assertThat(response.arguments().get("periodSeconds")).isEqualTo(5);
        assertThat(response.toolResult()).contains("\"feed\":\"events\"");
    }

    @Test
    void submitRegularSummaryIntentBuildsDigestCollector() {
        Day18AgentResponse response = service.submit("пиши сводку каждые 4 секунды по events");

        assertThat(response.tool()).isEqualTo("scheduler_add_collector");
        assertThat(response.arguments().get("feed")).isEqualTo("digest");
        assertThat(response.arguments().get("periodSeconds")).isEqualTo(4);
        assertThat(response.arguments().get("sourceFeed")).isEqualTo("events");
    }

    @Test
    void submitSummaryAggregatesCollectedData() {
        Day18AgentResponse created = service.submit("собирай данные каждые 5 секунд по events");
        String jobId = extractId(created.toolResult());
        scheduler.runNow(jobId);
        scheduler.runNow(jobId);

        Day18AgentResponse response = service.submit("дай сводку по events");

        assertThat(response.tool()).isEqualTo("scheduler_summary");
        assertThat(response.arguments().get("feed")).isEqualTo("events");
        assertThat(response.toolResult()).contains("\"count\":2");
        assertThat(response.answer()).contains("Результат инструмента:") .contains("\"count\":2");
    }

    @Test
    void submitSummaryWithSinceClauseParsesHours() {
        Day18AgentResponse response = service.submit("дай сводку по событиям за 2 часа");

        assertThat(response.tool()).isEqualTo("scheduler_summary");
        assertThat(response.arguments().get("feed")).isEqualTo("событиям");
        assertThat(response.arguments().get("sinceSeconds")).isEqualTo(7200);
    }

    @Test
    void submitListJobsChoosesListTool() {
        Day18AgentResponse response = service.submit("покажи задания в планировщике");

        assertThat(response.tool()).isEqualTo("scheduler_list_jobs");
        assertThat(response.toolError()).isFalse();
    }

    @Test
    void phraseWithLlmReturnsFramedAnswer() {
        when(llm.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Напомню через 2 секунды!", "stop"));

        Day18AgentResponse response = service.submit("напомни через 2 секунды выпить чай");

        assertThat(response.answer()).isEqualTo("Напомню через 2 секунды!");
        verify(llm).complete(any(CompletionCommand.class));
    }

    @Test
    void phraseFallsBackToRawResultWhenLlmFails() {
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("нет ключа"));

        Day18AgentResponse response = service.submit("напомни через 2 секунды выпить чай");

        assertThat(response.answer()).contains("Результат инструмента:").contains("выпить чай");
    }

    @Test
    void submitUnrecognizedPromptReturnsHint() {
        Day18AgentResponse response = service.submit("какая погода в Москве?");

        assertThat(response.tool()).isNull();
        assertThat(response.answer()).contains("Примеры запросов");
    }

    @Test
    void submitBlankPromptThrows() {
        assertThatThrownBy(() -> service.submit("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не может быть пустым");
    }

    private static String extractId(String json) {
        String key = "\"id\":\"";
        int start = json.indexOf(key) + key.length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}