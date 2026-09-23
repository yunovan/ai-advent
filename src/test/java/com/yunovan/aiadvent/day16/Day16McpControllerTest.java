package com.yunovan.aiadvent.day16;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

@WebMvcTest(controllers = {Day16McpController.class, ApiExceptionHandler.class})
class Day16McpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day16McpService service;

    @Test
    void getHealthReturnsConnectionInfo() throws Exception {
        when(service.health()).thenReturn(
                new Day16HealthResponse(true, "2024-11-05", "ai-advent-mcp", "0.1.0", "s1", 2));

        mockMvc.perform(get("/api/day16/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.protocolVersion").value("2024-11-05"))
                .andExpect(jsonPath("$.serverName").value("ai-advent-mcp"))
                .andExpect(jsonPath("$.serverVersion").value("0.1.0"))
                .andExpect(jsonPath("$.sessionId").value("s1"))
                .andExpect(jsonPath("$.toolCount").value(2));
    }

    @Test
    void getToolsReturnsToolList() throws Exception {
        when(service.tools()).thenReturn(List.of(
                new Day16ToolInfo("day16_sum", "Складывает два целых числа и возвращает результат."),
                new Day16ToolInfo("day16_upper", "Приводит переданный текст к верхнему регистру.")));

        mockMvc.perform(get("/api/day16/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("day16_sum"))
                .andExpect(jsonPath("$[1].description").value(containsString("верхнему регистру")));
    }

    @Test
    void postCallExecutesTool() throws Exception {
        when(service.call(eq("day16_sum"), any()))
                .thenReturn(new Day16CallResponse("day16_sum", "13", false));

        mockMvc.perform(post("/api/day16/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"day16_sum\",\"arguments\":{\"a\":6,\"b\":7}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool").value("day16_sum"))
                .andExpect(jsonPath("$.result").value("13"))
                .andExpect(jsonPath("$.isError").value(false));
    }

    @Test
    void getHealthMapsMcpFailureToBadGateway() throws Exception {
        when(service.health()).thenThrow(new Day16McpException("MCP недоступен: Connection refused"));

        mockMvc.perform(get("/api/day16/health"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("MCP недоступен: Connection refused"));
    }

    @Test
    void postCallMapsMcpFailureToBadGateway() throws Exception {
        when(service.call(eq("day16_sum"), any()))
                .thenThrow(new Day16McpException("Неизвестная сессия MCP"));

        mockMvc.perform(post("/api/day16/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"day16_sum\",\"arguments\":{\"a\":6,\"b\":7}}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Неизвестная сессия MCP"));
    }

    @Test
    void postCallMapsToolErrorToResponseWithIsError() throws Exception {
        when(service.call(eq("missing_tool"), any()))
                .thenReturn(new Day16CallResponse("missing_tool", "Инструмент не найден: missing_tool", true));

        mockMvc.perform(post("/api/day16/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"missing_tool\",\"arguments\":{}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isError").value(true))
                .andExpect(jsonPath("$.result").value(containsString("missing_tool")));
    }

    @Test
    void postCallWithMissingBodyPassesEmptyTool() throws Exception {
        when(service.call(eq(""), any()))
                .thenReturn(new Day16CallResponse("", "Инструмент не найден: ", true));

        mockMvc.perform(post("/api/day16/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isError").value(true));
    }

    @Test
    void postCallForwardsArgumentsAsMap() throws Exception {
        when(service.call(eq("day16_upper"), any()))
                .thenReturn(new Day16CallResponse("day16_upper", "ПРИВЕТ", false));

        mockMvc.perform(post("/api/day16/call")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tool\":\"day16_upper\",\"arguments\":{\"text\":\"привет\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ПРИВЕТ"));
    }
}