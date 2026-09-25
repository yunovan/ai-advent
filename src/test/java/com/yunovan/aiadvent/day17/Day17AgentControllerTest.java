package com.yunovan.aiadvent.day17;

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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {Day17AgentController.class, ApiExceptionHandler.class})
class Day17AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day17AgentService service;

    @Test
    void getHealthReturnsConnectionInfo() throws Exception {
        when(service.health()).thenReturn(new Day17HealthResponse(true, "ai-advent-tracker-mcp", "0.1.0", 3));

        mockMvc.perform(get("/api/day17/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.serverName").value("ai-advent-tracker-mcp"))
                .andExpect(jsonPath("$.serverVersion").value("0.1.0"))
                .andExpect(jsonPath("$.toolCount").value(3));
    }

    @Test
    void getToolsReturnsToolList() throws Exception {
        when(service.tools()).thenReturn(List.of(
                new Day17ToolInfo("tracker_create_task", "Создаёт задачу."),
                new Day17ToolInfo("tracker_list_tasks", "Список задач."),
                new Day17ToolInfo("tracker_add_comment", "Добавляет комментарий.")));

        mockMvc.perform(get("/api/day17/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("tracker_create_task"))
                .andExpect(jsonPath("$[2].description").value(containsString("комментарий")));
    }

    @Test
    void postAgentRunsThePrompt() throws Exception {
        when(service.submit(eq("создай задачу Привезти стол")))
                .thenReturn(new Day17AgentResponse("создай задачу Привезти стол",
                        "tracker_create_task", Map.of("title", "Привезти стол"),
                        "{\"id\":\"t-aaa\"}", false, "Задача создана!"));

        mockMvc.perform(post("/api/day17/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"создай задачу Привезти стол\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool").value("tracker_create_task"))
                .andExpect(jsonPath("$.arguments.title").value("Привезти стол"))
                .andExpect(jsonPath("$.toolError").value(false))
                .andExpect(jsonPath("$.answer").value("Задача создана!"));
    }

    @Test
    void postAgentWithMissingBodyPassesEmptyPrompt() throws Exception {
        when(service.submit(eq(""))).thenReturn(new Day17AgentResponse("", null, null, null, false, "hint"));

        mockMvc.perform(post("/api/day17/agent").contentType(MediaType.APPLICATION_JSON).content(""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool").doesNotExist());
    }

    @Test
    void getHealthMapsMcpFailureToBadGateway() throws Exception {
        when(service.health()).thenThrow(new Day17McpException("MCP недоступен: Connection refused"));

        mockMvc.perform(get("/api/day17/health"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("MCP недоступен: Connection refused"));
    }

    @Test
    void postAgentMapsBlankPromptToBadRequest() throws Exception {
        when(service.submit(any())).thenThrow(new IllegalArgumentException("Запрос не может быть пустым"));

        mockMvc.perform(post("/api/day17/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Запрос не может быть пустым"));
    }
}