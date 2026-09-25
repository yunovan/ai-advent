package com.yunovan.aiadvent.day17;

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

class Day17AgentServiceTest {

    @TempDir
    Path tempDir;

    private Day17McpServer server;
    private LlmClient llm;
    private Day17AgentService service;

    @BeforeEach
    void setUp() {
        server = new Day17McpServer(
                new Day17Properties(0, "/mcp", "ai-advent-tracker-mcp", "0.1.0", "dir"),
                new Day17TrackerService(new Day17TicketStore(tempDir)));
        server.start();
        Day17McpClient client = new Day17McpClient(
                new Day17Properties(server.boundPort(), "/mcp", "ai-advent-tracker-mcp", "0.1.0", "dir"));
        llm = mock(LlmClient.class);
        service = new Day17AgentService(client, llm);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void healthReportsConnectedServerAndToolCount() {
        Day17HealthResponse health = service.health();

        assertThat(health.connected()).isTrue();
        assertThat(health.serverName()).isEqualTo("ai-advent-tracker-mcp");
        assertThat(health.serverVersion()).isEqualTo("0.1.0");
        assertThat(health.toolCount()).isEqualTo(3);
    }

    @Test
    void toolsReturnsTheThreeTrackerTools() {
        List<Day17ToolInfo> tools = service.tools();

        assertThat(tools.stream().map(Day17ToolInfo::name)).containsExactlyInAnyOrder(
                "tracker_create_task", "tracker_list_tasks", "tracker_add_comment");
    }

    @Test
    void submitCreateTaskCallsMcpToolAndPhrasesAnswer() {
        when(llm.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Задача создана!", "stop"));

        Day17AgentResponse response = service.submit("создай задачу Привезти стол");

        assertThat(response.tool()).isEqualTo("tracker_create_task");
        assertThat(response.arguments().get("title")).isEqualTo("Привезти стол");
        assertThat(response.toolResult()).contains("t-").contains("Привезти стол");
        assertThat(response.toolError()).isFalse();
        assertThat(response.answer()).isEqualTo("Задача создана!");
        verify(llm).complete(any(CompletionCommand.class));
    }

    @Test
    void submitCreateTaskFallsBackToRawResultWhenLlmFails() {
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("нет ключа"));

        Day17AgentResponse response = service.submit("создай задачу Купить кресло");

        assertThat(response.tool()).isEqualTo("tracker_create_task");
        assertThat(response.toolResult()).contains("Купить кресло");
        assertThat(response.answer()).contains("Результат инструмента:").contains("Купить кресло");
    }

    @Test
    void submitListTasksChoosesToolAndKeepsStatusFilter() {
        service.submit("создай задачу Привезти стол");

        Day17AgentResponse response = service.submit("покажи задачи в работе");

        assertThat(response.tool()).isEqualTo("tracker_list_tasks");
        assertThat(response.arguments().get("status")).isEqualTo("in_progress");
        assertThat(response.toolError()).isFalse();
    }

    @Test
    void submitCommentChoosesToolAndParsesTaskId() {
        Day17AgentResponse created = service.submit("создай задачу Привезти стол");
        String taskId = extractId(created.toolResult());

        Day17AgentResponse response = service.submit("добавь комментарий к задаче " + taskId + ": проверил, всё ок");

        assertThat(response.tool()).isEqualTo("tracker_add_comment");
        assertThat(response.arguments().get("taskId")).isEqualTo(taskId);
        assertThat(response.arguments().get("text")).isEqualTo("проверил, всё ок");
        assertThat(response.toolResult()).contains("проверил, всё ок");
    }

    @Test
    void submitUnrecognizedPromptReturnsHint() {
        Day17AgentResponse response = service.submit("какая сегодня погода в Москве?");

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
        int start = json.indexOf("\"id\":\"t-") + "\"id\":\"".length();
        int end = json.indexOf('"', start);
        return json.substring(start, end);
    }
}