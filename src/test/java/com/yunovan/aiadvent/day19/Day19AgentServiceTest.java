package com.yunovan.aiadvent.day19;

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

class Day19AgentServiceTest {

    @TempDir
    Path tempDir;

    private Day19McpServer server;
    private Day19MarketService market;
    private LlmClient llm;
    private Day19AgentService service;

    @BeforeEach
    void setUp() {
        Day19SaveService saveService = new Day19SaveService(
                new Day19Properties(0, "/mcp", "ai-advent-pipeline-mcp", "0.1.0", tempDir.toString()));
        market = new Day19MarketService(new Day19CatalogService(), saveService);
        server = new Day19McpServer(
                new Day19Properties(0, "/mcp", "ai-advent-pipeline-mcp", "0.1.0", "dir"), market);
        server.start();
        Day19McpClient client = new Day19McpClient(
                new Day19Properties(server.boundPort(), "/mcp", "ai-advent-pipeline-mcp", "0.1.0", "dir"));
        llm = mock(LlmClient.class);
        service = new Day19AgentService(client, llm, market);
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void healthReportsConnectedServerAndThreeTools() {
        Day19HealthResponse health = service.health();

        assertThat(health.connected()).isTrue();
        assertThat(health.serverName()).isEqualTo("ai-advent-pipeline-mcp");
        assertThat(health.serverVersion()).isEqualTo("0.1.0");
        assertThat(health.toolCount()).isEqualTo(3);
    }

    @Test
    void toolsReturnsTheThreePipelineTools() {
        List<Day19ToolInfo> tools = service.tools();

        assertThat(tools.stream().map(Day19ToolInfo::name))
                .containsExactlyInAnyOrder("search", "summarize", "saveToFile");
    }

    @Test
    void pipelineRunsAllThreeStepsAndSavesFile() {
        Day19PipelineResponse response = service.pipeline("ноутбук", "markdown", "laptops-t");

        assertThat(response.query()).isEqualTo("ноутбук");
        assertThat(response.format()).isEqualTo("markdown");
        assertThat(response.steps()).hasSize(3);
        assertThat(response.steps().get(0).tool()).isEqualTo("search");
        assertThat(response.steps().get(0).success()).isTrue();
        assertThat(response.steps().get(0).note()).contains("5");
        assertThat(response.steps().get(1).tool()).isEqualTo("summarize");
        assertThat(response.steps().get(1).success()).isTrue();
        assertThat(response.steps().get(2).tool()).isEqualTo("saveToFile");
        assertThat(response.steps().get(2).success()).isTrue();
        assertThat(response.saved()).isNotNull();
        assertThat(response.saved().fileName()).isEqualTo("laptops-t.md");
        assertThat(response.saved().bytes()).isPositive();
        assertThat(Path.of(response.saved().path())).exists();
    }

    @Test
    void submitSearchIntentOnlyCallsSearch() {
        Day19AgentResponse response = service.submit("найди наушники");

        assertThat(response.intent()).isEqualTo("search");
        assertThat(response.tool()).isEqualTo("search");
        assertThat(response.arguments().get("query")).isEqualTo("наушники");
        assertThat(response.toolResult()).contains("\"products\"");
        assertThat(response.toolResult()).contains("JBL Tune 770NC");
    }

    @Test
    void submitCompareIntentBuildsSummaryTable() {
        Day19AgentResponse response = service.submit("сравни смартфоны в таблицу");

        assertThat(response.intent()).isEqualTo("compare");
        assertThat(response.tool()).isEqualTo("summarize");
        assertThat(response.arguments().get("query")).isEqualTo("смартфоны");
        assertThat(response.arguments().get("format")).isEqualTo("markdown");
        assertThat(response.toolResult()).startsWith("Сравнение по запросу «смартфоны» — товаров: 4");
    }

    @Test
    void submitPipelineIntentSavesFileAndReturnsAnswer() {
        Day19AgentResponse response = service.submit("сохрани телевизоры в файл csv");

        assertThat(response.intent()).isEqualTo("pipeline");
        assertThat(response.tool()).isEqualTo("saveToFile");
        assertThat(response.arguments().get("format")).isEqualTo("csv");
        assertThat(response.toolResult()).contains("Файл сохранён:");
        assertThat(response.toolResult()).contains(".csv");
        assertThat(response.answer()).contains("Результат инструмента");
    }

    @Test
    void submitPipelineIntentExtractsFileName() {
        Day19AgentResponse response = service.submit("сохрани телевизоры в файл ТВ-2026");

        assertThat(response.intent()).isEqualTo("pipeline");
        assertThat(response.arguments().get("fileName")).isEqualTo("ТВ-2026");
        assertThat(response.arguments().get("format")).isEqualTo("markdown");
        assertThat(response.toolResult()).contains("Файл сохранён:");
        assertThat(response.toolResult()).contains("ТВ-2026");
    }

    @Test
    void phraseWithLlmReturnsFramedAnswer() {
        when(llm.complete(any(CompletionCommand.class)))
                .thenReturn(new LlmReply("Нашёл 4 смартфона!", "stop"));

        Day19AgentResponse response = service.submit("сравни смартфоны в таблицу");

        assertThat(response.answer()).isEqualTo("Нашёл 4 смартфона!");
        verify(llm).complete(any(CompletionCommand.class));
    }

    @Test
    void phraseFallsBackToRawResultWhenLlmFails() {
        when(llm.complete(any(CompletionCommand.class))).thenThrow(new LlmException("нет ключа"));

        Day19AgentResponse response = service.submit("сравни смартфоны в таблицу");

        assertThat(response.answer()).contains("Результат инструмента").contains("смартфоны");
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