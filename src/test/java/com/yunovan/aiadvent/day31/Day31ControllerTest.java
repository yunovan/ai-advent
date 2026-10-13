package com.yunovan.aiadvent.day31;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import com.yunovan.aiadvent.day21.Day21Chunk;
import com.yunovan.aiadvent.day21.Day21SearchHit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day31Controller.class, ApiExceptionHandler.class})
class Day31ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day31DeveloperAssistantService service;

    private static Day21SearchHit hit() {
        Day21Chunk chunk = new Day21Chunk("fixed", "docs/architecture.md", "architecture.md",
                "Архитектура проекта", "Слои RAG", "docs/architecture.md#fixed#0001", 0, 60,
                "## Слои RAG\nПроект использует локальные эмбеддинги и индекс.");
        return new Day21SearchHit(chunk, 0.87, "Слои RAG …");
    }

    private static Day31HealthResponse health() {
        return new Day31HealthResponse("ok", "http://localhost:9095/mcp", "/repo", "main", false,
                List.of("README.md", "architecture.md"), 42, 12_000,
                new Day31McpStatus(true, "http://localhost:9095/mcp", 5, ""),
                new Day31ModelStatus("http://localhost:11434", "qwen2.5:3b", true, "0.35.1", true, ""));
    }

    private static Day31HelpResponse help() {
        return new Day31HelpResponse("Как устроен RAG?", "RAG описан в docs/architecture.md", false,
                "qwen2.5:3b", "main", false, "Инструмент MCP git_branch:\nВетка: main", true, 5, 1,
                List.of(hit()), List.of("architecture.md"), 82.0, 900L, 120, 40, 9.5, "");
    }

    @Test
    void healthReturnsRepositoryDocsAndMcp() throws Exception {
        when(service.health()).thenReturn(health());

        mockMvc.perform(get("/api/day31/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.branch").value("main"))
                .andExpect(jsonPath("$.documents[1]").value("architecture.md"))
                .andExpect(jsonPath("$.mcp.available").value(true))
                .andExpect(jsonPath("$.mcp.tools").value(5))
                .andExpect(jsonPath("$.model.installed").value(true));
    }

    @Test
    void docsListsCorpus() throws Exception {
        when(service.docs()).thenReturn(List.of(
                new Day31DocumentInfo("docs/architecture.md", "architecture.md", "Архитектура", "markdown",
                        3000, 6)));

        mockMvc.perform(get("/api/day31/docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("docs/architecture.md"))
                .andExpect(jsonPath("$[0].chunks").value(6));
    }

    @Test
    void toolsExposeMcpCatalog() throws Exception {
        when(service.tools()).thenReturn(new Day31ToolsResponse(true,
                "http://localhost:9095/mcp", 1, List.of(new Day31ToolInfo("git_branch", "ветка")), ""));

        mockMvc.perform(get("/api/day31/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.tools[0].name").value("git_branch"));
    }

    @Test
    void gitCallsMcpTool() throws Exception {
        when(service.git(any(), any())).thenReturn(new Day31GitResponse("git_branch", true, "Ветка: main"));

        mockMvc.perform(post("/api/day31/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"git_branch\",\"arguments\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool").value("git_branch"))
                .andExpect(jsonPath("$.content").value("Ветка: main"));
    }

    @Test
    void gitWithUnknownToolReturnsBadRequest() throws Exception {
        when(service.git(any(), any()))
                .thenThrow(new IllegalArgumentException("Неизвестный git-инструмент"));

        mockMvc.perform(post("/api/day31/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Неизвестный git-инструмент"));
    }

    @Test
    void gitWhenMcpUnavailableReturnsBadGateway() throws Exception {
        when(service.git(any(), any())).thenThrow(new Day31GitException("MCP недоступен"));

        mockMvc.perform(post("/api/day31/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"git_branch\"}"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void helpReturnsAnswerSourcesAndMetrics() throws Exception {
        when(service.help(any())).thenReturn(help());

        mockMvc.perform(post("/api/day31/help")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Как устроен RAG?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("RAG описан в docs/architecture.md"))
                .andExpect(jsonPath("$.mcpUsed").value(true))
                .andExpect(jsonPath("$.gitBranch").value("main"))
                .andExpect(jsonPath("$.sourceFiles[0]").value("architecture.md"))
                .andExpect(jsonPath("$.groundingPercent").value(82.0))
                .andExpect(jsonPath("$.latencyMs").value(900));
    }

    @Test
    void helpWithBlankQuestionReturnsBadRequest() throws Exception {
        when(service.help(any()))
                .thenThrow(new IllegalArgumentException("Вопрос не может быть пустым"));

        mockMvc.perform(post("/api/day31/help")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Вопрос не может быть пустым"));
    }

    @Test
    void gitDelegatesArguments() throws Exception {
        when(service.git(any(), any())).thenReturn(new Day31GitResponse("git_files", true, "a.java\nb.java"));

        mockMvc.perform(post("/api/day31/git")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"git_files\",\"arguments\":{\"prefix\":\"src\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool").value("git_files"));
    }
}
