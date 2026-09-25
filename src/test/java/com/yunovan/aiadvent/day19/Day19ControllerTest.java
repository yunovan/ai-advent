package com.yunovan.aiadvent.day19;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.yunovan.aiadvent.day01.ApiExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day19Controller.class, ApiExceptionHandler.class})
class Day19ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day19AgentService service;

    @MockitoBean
    private Day19MarketApi market;

    @MockitoBean
    private Day19SaveService saveService;

    @Test
    void getHealthReturnsConnectionInfo() throws Exception {
        when(service.health()).thenReturn(new Day19HealthResponse(true, "ai-advent-pipeline-mcp", "0.1.0", 3));

        mockMvc.perform(get("/api/day19/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.serverName").value("ai-advent-pipeline-mcp"))
                .andExpect(jsonPath("$.serverVersion").value("0.1.0"))
                .andExpect(jsonPath("$.toolCount").value(3));
    }

    @Test
    void getToolsReturnsToolList() throws Exception {
        when(service.tools()).thenReturn(List.of(
                new Day19ToolInfo("search", "Ищет товары."),
                new Day19ToolInfo("summarize", "Строит сводную таблицу."),
                new Day19ToolInfo("saveToFile", "Сохраняет файл.")));

        mockMvc.perform(get("/api/day19/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("search"))
                .andExpect(jsonPath("$[2].name").value("saveToFile"));
    }

    @Test
    void postSearchReturnsFoundProducts() throws Exception {
        when(market.search(eq("ноутбук"), any(), any(), any())).thenReturn(List.of(
                new Day19Product("p01", "Ноутбук Lenovo IdeaPad 3", "ноутбуки", "DNS",
                        "https://www.dns-shop.ru/lenovo-ideapad-3", 54990, "RUB", 4.6, Map.of("Память", "16 ГБ"))));

        mockMvc.perform(post("/api/day19/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"ноутбук\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("p01"))
                .andExpect(jsonPath("$[0].price").value(54990.0))
                .andExpect(jsonPath("$[0].params.Память").value("16 ГБ"));
    }

    @Test
    void postSummarizeReturnsTable() throws Exception {
        when(market.summarizeQuery(eq("ноутбук"), eq("markdown")))
                .thenReturn("Сравнение по запросу «ноутбук» — товаров: 5");

        mockMvc.perform(post("/api/day19/summarize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"ноутбук\",\"format\":\"markdown\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("Сравнение по запросу «ноутбук» — товаров: 5"));
    }

    @Test
    void postSaveReturnsSavedFile() throws Exception {
        when(market.saveQuery(eq("телевизоры"), eq("csv"), eq("тв-2026"))).thenReturn(
                new Day19SavedFile("тв-2026.csv", "data/day19-market/тв-2026.csv", "csv", 321, null));

        mockMvc.perform(post("/api/day19/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"телевизоры\",\"format\":\"csv\",\"fileName\":\"тв-2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("тв-2026.csv"))
                .andExpect(jsonPath("$.bytes").value(321));
    }

    @Test
    void postPipelineRunsFullChain() throws Exception {
        when(service.pipeline(eq("ноутбук"), eq("markdown"), eq("laptops"))).thenReturn(
                new Day19PipelineResponse("ноутбук", "markdown", List.of(
                        new Day19PipelineStep("search", true, "получено 5 товаров"),
                        new Day19PipelineStep("summarize", true, "таблица готова к сохранению"),
                        new Day19PipelineStep("saveToFile", true, "файл записан")),
                        new Day19SavedFile("laptops.md", "data/day19-market/laptops.md", "markdown", 410, null)));

        mockMvc.perform(post("/api/day19/pipeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"ноутбук\",\"format\":\"markdown\",\"fileName\":\"laptops\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps.length()").value(3))
                .andExpect(jsonPath("$.steps[0].tool").value("search"))
                .andExpect(jsonPath("$.steps[2].success").value(true))
                .andExpect(jsonPath("$.saved.fileName").value("laptops.md"));
    }

    @Test
    void postAgentRunsThePrompt() throws Exception {
        when(service.submit(eq("сравни ноутбуки в таблицу")))
                .thenReturn(new Day19AgentResponse("сравни ноутбуки в таблицу",
                        "compare", "summarize", Map.of("query", "ноутбуки", "format", "markdown"),
                        "Сравнение по запросу «ноутбуки»", "Таблица выше!"));

        mockMvc.perform(post("/api/day19/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"сравни ноутбуки в таблицу\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("compare"))
                .andExpect(jsonPath("$.tool").value("summarize"))
                .andExpect(jsonPath("$.arguments.query").value("ноутбуки"))
                .andExpect(jsonPath("$.answer").value("Таблица выше!"));
    }

    @Test
    void getFilesReturnsSavedList() throws Exception {
        when(saveService.list()).thenReturn(List.of(
                new Day19SavedFile("laptops.md", "data/day19-market/laptops.md", "markdown", 410, null)));

        mockMvc.perform(get("/api/day19/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fileName").value("laptops.md"));
    }

    @Test
    void postAgentMapsIllegalArgumentToBadRequest() throws Exception {
        when(service.submit(any())).thenThrow(new IllegalArgumentException("Запрос не может быть пустым"));

        mockMvc.perform(post("/api/day19/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"какая погода в Москве?\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Запрос не может быть пустым"));
    }

    @Test
    void postPipelineMapsMcpFailureToBadGateway() throws Exception {
        when(service.pipeline(any(), any(), any())).thenThrow(new Day19McpException("MCP недоступен"));

        mockMvc.perform(post("/api/day19/pipeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"ноутбук\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("MCP недоступен"));
    }
}