package com.yunovan.aiadvent.day31;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21Document;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import com.yunovan.aiadvent.day26.Day26Answer;
import com.yunovan.aiadvent.day26.Day26ChatOptions;
import com.yunovan.aiadvent.day26.Day26InstalledModel;
import com.yunovan.aiadvent.day26.Day26LlmException;
import com.yunovan.aiadvent.day26.Day26LocalLlmClient;
import com.yunovan.aiadvent.day26.Day26Properties;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class Day31DeveloperAssistantServiceTest {

    private final Day31Properties properties = new Day31Properties(null, null, null, null, null,
            null, null, 5, 12, 700, 80, null, 400, null, null, null);
    private final Day31ProjectIndex index = mock(Day31ProjectIndex.class);
    private final Day31McpClient mcp = mock(Day31McpClient.class);
    private final Day31GitTool git = mock(Day31GitTool.class);
    private final Day26LocalLlmClient llm = mock(Day26LocalLlmClient.class);
    private final Day26Properties llmProperties = new Day26Properties(null, null, null, null, null, null);

    private Day31DeveloperAssistantService service;

    @BeforeEach
    void setUp() {
        service = new Day31DeveloperAssistantService(properties, index, mcp, git, llm, llmProperties);
    }

    private static Day21Document document() {
        return new Day21Document("docs/architecture.md", "architecture.md", "Архитектура проекта",
                "markdown", "## Слои RAG\nПроект использует локальные эмбеддинги и индекс.");
    }

    private static Day21Chunk chunk() {
        return new Day21Chunk("fixed", "docs/architecture.md", "architecture.md", "Архитектура проекта",
                "Слои RAG", "docs/architecture.md#fixed#0001", 0, 60,
                "## Слои RAG\nПроект использует локальные эмбеддинги и индекс.");
    }

    private static Day21SearchHit hit() {
        return new Day21SearchHit(chunk(), 0.87,
                "Слои RAG Проект использует локальные эмбеддинги и индекс.");
    }

    private void stubSnapshot() {
        when(index.snapshot()).thenReturn(new Day31ProjectIndex.Snapshot(
                List.of(document()), List.of(chunk()), List.of(), 60));
    }

    private void stubGitAvailable() {
        when(git.info()).thenReturn(new Day31GitInfo(true, "main", "abc1234", false, ""));
        when(git.repositoryDir()).thenReturn(Path.of("."));
    }

    private void stubMcpAvailable() {
        Day31Connection connection = new Day31Connection("2024-11-05", "ai-advent-git-mcp", "0.1.0", "sess");
        when(mcp.connect()).thenReturn(connection);
        when(mcp.listTools(any()))
                .thenReturn(List.of(new Day31ToolInfo("git_branch", "ветка")));
        when(mcp.callTool(any(), eq("git_branch"), any()))
                .thenReturn(new Day31ToolResult("git_branch", "Ветка: main", false));
        when(mcp.callTool(any(), eq("git_status"), any()))
                .thenReturn(new Day31ToolResult("git_status", "Ветка: main\nчисто", false));
    }

    @Test
    void healthReportsRepositoryDocsAndMcp() {
        stubSnapshot();
        stubGitAvailable();
        stubMcpAvailable();
        when(llm.version()).thenReturn("0.35.1");
        when(llm.models()).thenReturn(List.of(new Day26InstalledModel("qwen2.5:3b", 1L, "3.1B", "Q4_K_M")));

        Day31HealthResponse health = service.health();

        assertThat(health.status()).isEqualTo("ok");
        assertThat(health.branch()).isEqualTo("main");
        assertThat(health.documents()).contains("architecture.md");
        assertThat(health.mcp().available()).isTrue();
        assertThat(health.mcp().tools()).isEqualTo(1);
        assertThat(health.model().available()).isTrue();
        assertThat(health.model().installed()).isTrue();
    }

    @Test
    void healthMarksMcpUnavailable() {
        stubSnapshot();
        stubGitAvailable();
        when(mcp.connect()).thenThrow(new Day31McpException("нет соединения"));
        when(llm.version()).thenReturn("0.35.1");
        when(llm.models()).thenReturn(List.of(new Day26InstalledModel("qwen2.5:3b", 1L, "3.1B", "Q4_K_M")));

        Day31HealthResponse health = service.health();

        assertThat(health.mcp().available()).isFalse();
        assertThat(health.mcp().reason()).contains("нет соединения");
    }

    @Test
    void docsListsProjectDocumentation() {
        stubSnapshot();

        List<Day31DocumentInfo> docs = service.docs();

        assertThat(docs).hasSize(1);
        assertThat(docs.getFirst().source()).isEqualTo("docs/architecture.md");
        assertThat(docs.getFirst().chunks()).isEqualTo(1);
    }

    @Test
    void toolsComeFromMcpServer() {
        stubMcpAvailable();

        Day31ToolsResponse tools = service.tools();

        assertThat(tools.available()).isTrue();
        assertThat(tools.count()).isEqualTo(1);
        assertThat(tools.tools().getFirst().name()).isEqualTo("git_branch");
    }

    @Test
    void gitDelegatesToMcpTool() {
        stubMcpAvailable();

        Day31GitResponse response = service.git("branch", Map.of());

        assertThat(response.tool()).isEqualTo("git_branch");
        assertThat(response.ok()).isTrue();
        assertThat(response.content()).contains("Ветка: main");
    }

    @Test
    void gitRejectsUnknownTool() {
        assertThatThrownBy(() -> service.git("mystery", Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void helpAnswersFromDocsAndGitThroughMcp() {
        stubSnapshot();
        stubGitAvailable();
        stubMcpAvailable();
        when(index.search(anyString(), anyInt())).thenReturn(List.of(hit()));
        when(llm.chat(anyList(), any(Day26ChatOptions.class))).thenReturn(new Day26Answer(
                "prompt", "RAG описан в docs/architecture.md — там локальные эмбеддинги и индекс.",
                "qwen2.5:3b", "http://localhost:11434", 900L, 120, 40, 9.5));

        Day31HelpResponse response = service.help("Как устроен RAG?");

        assertThat(response.fallback()).isFalse();
        assertThat(response.answer()).contains("architecture.md");
        assertThat(response.mcpUsed()).isTrue();
        assertThat(response.mcpTools()).isEqualTo(1);
        assertThat(response.gitBranch()).isEqualTo("main");
        assertThat(response.retrieved()).isEqualTo(1);
        assertThat(response.sourceFiles()).contains("architecture.md");
        assertThat(response.latencyMs()).isEqualTo(900L);
        assertThat(response.groundingPercent()).isNotNull();
    }

    @Test
    void helpFallsBackToDocsWhenModelUnavailable() {
        stubSnapshot();
        stubGitAvailable();
        stubMcpAvailable();
        when(index.search(anyString(), anyInt())).thenReturn(List.of(hit()));
        when(llm.chat(anyList(), any(Day26ChatOptions.class)))
                .thenThrow(new Day26LlmException("Ollama недоступна"));

        Day31HelpResponse response = service.help("Как устроен RAG?");

        assertThat(response.fallback()).isTrue();
        assertThat(response.answer()).contains("docs/architecture.md");
        assertThat(response.note()).contains("Ollama недоступна");
    }

    @Test
    void helpRejectsBlankQuestion() {
        assertThatThrownBy(() -> service.help("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
