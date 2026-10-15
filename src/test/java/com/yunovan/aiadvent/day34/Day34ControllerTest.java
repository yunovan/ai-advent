package com.yunovan.aiadvent.day34;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day34Controller.class, ApiExceptionHandler.class})
class Day34ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day34FileService service;

    private static Day34HealthResponse health() {
        return new Day34HealthResponse("ok", "C:/project", true, 120, 8, 400, 200_000,
                new Day34McpStatus(true, "http://localhost:9097/mcp", 6, ""),
                new Day34ModelStatus("http://localhost:11434", "qwen2.5:3b", true, "0.35.1", true, ""));
    }

    private static Day34RunResponse run() {
        return new Day34RunResponse("usages", "Поиск использований", "Найди Widget", "Widget",
                List.of(), List.of(), List.of("Соединение: mcp"), 2, List.of("A.java", "B.java"),
                2, List.of(), List.of(), "", List.of(), "Найдено 2 использований",
                "# Использования", false, "qwen2.5:3b", 850L, 120, 60, 11.0, "Файловые операции: mcp");
    }

    @Test
    void healthReturnsWorkspaceMcpAndModel() throws Exception {
        when(service.health()).thenReturn(health());

        mockMvc.perform(get("/api/day34/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.workspaceAvailable").value(true))
                .andExpect(jsonPath("$.codeFiles").value(120))
                .andExpect(jsonPath("$.mcp.available").value(true))
                .andExpect(jsonPath("$.mcp.tools").value(6))
                .andExpect(jsonPath("$.model.installed").value(true));
    }

    @Test
    void filesListReturnsEntries() throws Exception {
        when(service.files(any(), any())).thenReturn(List.of(
                new Day34FileInfo("src/Foo.java", 120, 10, "")));

        mockMvc.perform(get("/api/day34/files").param("prefix", "src"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value("src/Foo.java"))
                .andExpect(jsonPath("$[0].lines").value(10));
    }

    @Test
    void readReturnsContent() throws Exception {
        when(service.read(any())).thenReturn(new Day34FileContent(
                "src/Foo.java", 1, 2, 10, "class Foo {}", false));

        mockMvc.perform(post("/api/day34/read")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"path\":\"src/Foo.java\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value("src/Foo.java"))
                .andExpect(jsonPath("$.content").value("class Foo {}"));
    }

    @Test
    void searchReturnsMatches() throws Exception {
        when(service.search(any())).thenReturn(List.of(
                new Day34SearchMatch("src/Foo.java", 2, "Widget w = new Widget();")));

        mockMvc.perform(post("/api/day34/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pattern\":\"Widget\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].line").value(2))
                .andExpect(jsonPath("$[0].path").value("src/Foo.java"));
    }

    @Test
    void runReturnsAssistantResult() throws Exception {
        when(service.run(any())).thenReturn(run());

        mockMvc.perform(post("/api/day34/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"Найди Widget\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task").value("usages"))
                .andExpect(jsonPath("$.matches").value(2))
                .andExpect(jsonPath("$.filesTouched").value(2));
    }

    @Test
    void badRequestWhenGoalMissing() throws Exception {
        when(service.run(any())).thenThrow(new IllegalArgumentException("Не указана цель"));

        mockMvc.perform(post("/api/day34/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void badGatewayWhenMcpFails() throws Exception {
        when(service.tools()).thenThrow(new Day34McpException("MCP недоступен"));

        mockMvc.perform(get("/api/day34/tools"))
                .andExpect(status().isBadGateway());
    }
}
