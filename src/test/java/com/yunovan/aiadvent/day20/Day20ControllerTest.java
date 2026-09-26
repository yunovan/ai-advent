package com.yunovan.aiadvent.day20;

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

@WebMvcTest(controllers = {Day20Controller.class, ApiExceptionHandler.class})
class Day20ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day20Orchestrator orchestrator;

    @MockitoBean
    private Day20AgentService service;

    @Test
    void getHealthReturnsOrchestratorStatus() throws Exception {
        when(orchestrator.health()).thenReturn(new Day20HealthResponse(true, List.of(
                new Day20ServerInfo("scheduler", "ai-advent-scheduler-mcp", "0.1.0", 6, true),
                new Day20ServerInfo("market", "ai-advent-pipeline-mcp", "0.1.0", 3, true)), 9));

        mockMvc.perform(get("/api/day20/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.servers.length()").value(2))
                .andExpect(jsonPath("$.servers[0].server").value("scheduler"))
                .andExpect(jsonPath("$.servers[1].toolCount").value(3))
                .andExpect(jsonPath("$.toolCount").value(9));
    }

    @Test
    void getServersReturnsRegisteredServers() throws Exception {
        when(orchestrator.health()).thenReturn(new Day20HealthResponse(true, List.of(
                new Day20ServerInfo("scheduler", "ai-advent-scheduler-mcp", "0.1.0", 6, true),
                new Day20ServerInfo("market", "ai-advent-pipeline-mcp", "0.1.0", 3, true)), 9));

        mockMvc.perform(get("/api/day20/servers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].server").value("scheduler"))
                .andExpect(jsonPath("$[1].server").value("market"));
    }

    @Test
    void getToolsReturnsAggregatedToolList() throws Exception {
        when(orchestrator.tools()).thenReturn(List.of(
                new Day20ToolEntry("scheduler", "scheduler_summary", "Сводка."),
                new Day20ToolEntry("market", "search", "Поиск.")));

        mockMvc.perform(get("/api/day20/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].server").value("scheduler"))
                .andExpect(jsonPath("$[1].name").value("search"));
    }

    @Test
    void getFlowsReturnsFlowDefinitions() throws Exception {
        when(orchestrator.flows()).thenReturn(List.of(
                new Day20FlowDefinition("market-report", "Флоу", List.of())));

        mockMvc.perform(get("/api/day20/flows"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("market-report"));
    }

    @Test
    void postRouteRunsToolOnRightServer() throws Exception {
        when(orchestrator.route(eq("search"), any())).thenReturn(
                new Day20CallResponse("market", "search", Map.of("query", "ноутбук"), true, "{\"products\":[]}"));

        mockMvc.perform(post("/api/day20/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"search\",\"arguments\":{\"query\":\"ноутбук\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server").value("market"))
                .andExpect(jsonPath("$.tool").value("search"))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void postCallInvokesToolOnNamedServer() throws Exception {
        when(orchestrator.call(eq("scheduler"), eq("scheduler_list_jobs"), any())).thenReturn(
                new Day20CallResponse("scheduler", "scheduler_list_jobs", Map.of(), true, "[\"j-1\"]"));

        mockMvc.perform(post("/api/day20/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"server\":\"scheduler\",\"tool\":\"scheduler_list_jobs\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.server").value("scheduler"))
                .andExpect(jsonPath("$.result").value("[\"j-1\"]"));
    }

    @Test
    void postFlowRunsLongFlow() throws Exception {
        when(orchestrator.runFlow(eq("market-report"), any())).thenReturn(new Day20FlowResponse(
                "market-report", "Флоу", Map.of("query", "ноутбук"), List.of(
                        new Day20FlowStepResult("market", "search", Map.of("query", "ноутбук"),
                                true, "[{\"products\":5}]"),
                        new Day20FlowStepResult("scheduler", "scheduler_add_reminder",
                                Map.of("topic", "Готово"), true, "{}")),
                "Флоу «market-report» — шагов: 2, успешно: 2/2"));

        mockMvc.perform(post("/api/day20/flow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flow\":\"market-report\",\"arguments\":{\"query\":\"ноутбук\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.steps.length()").value(2))
                .andExpect(jsonPath("$.steps[1].server").value("scheduler"))
                .andExpect(jsonPath("$.summary").value("Флоу «market-report» — шагов: 2, успешно: 2/2"));
    }

    @Test
    void postAgentRunsThePrompt() throws Exception {
        when(service.submit(eq("сравни смартфоны в таблицу"))).thenReturn(new Day20AgentResponse(
                "сравни смартфоны в таблицу", "market", "market", "summarize",
                Map.of("query", "смартфоны", "format", "markdown"),
                "Сравнение по запросу «смартфоны»", "Таблица выше!"));

        mockMvc.perform(post("/api/day20/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"сравни смартфоны в таблицу\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("market"))
                .andExpect(jsonPath("$.tool").value("summarize"))
                .andExpect(jsonPath("$.answer").value("Таблица выше!"));
    }

    @Test
    void postRouteMapsBadToolToBadRequest() throws Exception {
        when(orchestrator.route(eq("calculator"), any()))
                .thenThrow(new IllegalArgumentException("Инструмент «calculator» не зарегистрирован"));

        mockMvc.perform(post("/api/day20/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"calculator\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Инструмент «calculator» не зарегистрирован"));
    }

    @Test
    void postFlowMapsMcpFailureToBadGateway() throws Exception {
        when(orchestrator.runFlow(eq("market-report"), any()))
                .thenThrow(new Day20McpException("Сервер «market» недоступен"));

        mockMvc.perform(post("/api/day20/flow")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flow\":\"market-report\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Сервер «market» недоступен"));
    }
}