package com.yunovan.aiadvent.day18;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

@WebMvcTest(controllers = {Day18Controller.class, ApiExceptionHandler.class})
class Day18ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private Day18AgentService service;

    @MockitoBean
    private Day18SchedulerApi scheduler;

    private Day18Job job(String id, String status) {
        Day18Job job = new Day18Job(id, "collector", "events", "events", status, null, 5,
                null, null, "2026-01-01T00:00:00Z", null, "2026-01-01T00:00:05Z");
        job.setRunCount(3);
        job.setLastResult("ping: мок-замер");
        return job;
    }

    @Test
    void getHealthReturnsConnectionInfo() throws Exception {
        when(service.health()).thenReturn(new Day18HealthResponse(true, "ai-advent-scheduler-mcp", "0.1.0", 6));

        mockMvc.perform(get("/api/day18/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.serverName").value("ai-advent-scheduler-mcp"))
                .andExpect(jsonPath("$.serverVersion").value("0.1.0"))
                .andExpect(jsonPath("$.toolCount").value(6));
    }

    @Test
    void getToolsReturnsToolList() throws Exception {
        when(service.tools()).thenReturn(List.of(
                new Day18ToolInfo("scheduler_add_reminder", "Напоминание."),
                new Day18ToolInfo("scheduler_add_collector", "Периодический сбор."),
                new Day18ToolInfo("scheduler_list_jobs", "Список заданий."),
                new Day18ToolInfo("scheduler_summary", "Сводка."),
                new Day18ToolInfo("scheduler_run_now", "Выполнить сейчас."),
                new Day18ToolInfo("scheduler_stop_process", "Остановить процесс.")));

        mockMvc.perform(get("/api/day18/tools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].name").value("scheduler_add_reminder"))
                .andExpect(jsonPath("$[4].description").value(containsString("сейчас")));
    }

    @Test
    void postAgentRunsThePrompt() throws Exception {
        when(service.submit(eq("напомни через 10 секунд выпить чай")))
                .thenReturn(new Day18AgentResponse("напомни через 10 секунд выпить чай",
                        "scheduler_add_reminder", Map.of("topic", "выпить чай", "delaySeconds", 10),
                        "{\"id\":\"j-aaa\"}", false, "Напомню!"));

        mockMvc.perform(post("/api/day18/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"напомни через 10 секунд выпить чай\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tool").value("scheduler_add_reminder"))
                .andExpect(jsonPath("$.arguments.topic").value("выпить чай"))
                .andExpect(jsonPath("$.toolError").value(false))
                .andExpect(jsonPath("$.answer").value("Напомню!"));
    }

    @Test
    void getJobsReturnsJobList() throws Exception {
        when(scheduler.listJobs()).thenReturn(List.of(job("j-1", "active"), job("j-2", "done")));

        mockMvc.perform(get("/api/day18/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("j-1"))
                .andExpect(jsonPath("$[0].status").value("active"))
                .andExpect(jsonPath("$[0].runCount").value(3));
    }

    @Test
    void postReminderCreatesJob() throws Exception {
        when(scheduler.addReminder(eq("выпить чай"), eq(30)))
                .thenReturn(job("j-r1", "pending"));

        mockMvc.perform(post("/api/day18/reminder")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"topic\":\"выпить чай\",\"delaySeconds\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("j-r1"))
                .andExpect(jsonPath("$.status").value("pending"));
    }

    @Test
    void postCollectorCreatesCollector() throws Exception {
        when(scheduler.addCollector(eq("events"), eq(5), isNull(), isNull()))
                .thenReturn(job("j-c1", "active"));

        mockMvc.perform(post("/api/day18/collector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"feed\":\"events\",\"periodSeconds\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("j-c1"))
                .andExpect(jsonPath("$.periodSeconds").value(5));
    }

    @Test
    void postRunExecutesJob() throws Exception {
        when(scheduler.runNow(eq("j-c1"))).thenReturn(job("j-c1", "active"));

        mockMvc.perform(post("/api/day18/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":\"j-c1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("j-c1"));
    }

    @Test
    void postStopStopsJob() throws Exception {
        when(scheduler.stopProcess(eq("j-c1")))
                .thenReturn(List.of(job("j-c1", "stopped")));

        mockMvc.perform(post("/api/day18/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobId\":\"j-c1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("j-c1"))
                .andExpect(jsonPath("$[0].status").value("stopped"));
    }

    @Test
    void postStopWithoutJobIdStopsAll() throws Exception {
        when(scheduler.stopProcess(isNull()))
                .thenReturn(List.of(job("j-1", "stopped"), job("j-2", "stopped")));

        mockMvc.perform(post("/api/day18/stop")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getSummaryReturnsAggregation() throws Exception {
        when(scheduler.summary(eq("events"), isNull())).thenReturn(new Day18Summary(
                "events", null, 3, "2026-01-01T00:00:01Z", "2026-01-01T00:00:03Z",
                1.5, 1.0, 2.0, null, "мок-замер №3", List.of()));

        mockMvc.perform(get("/api/day18/summary").param("feed", "events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.avgValue").value(1.5))
                .andExpect(jsonPath("$.lastPayload").value("мок-замер №3"));
    }

    @Test
    void postAgentMapsBlankPromptToBadRequest() throws Exception {
        when(service.submit(any())).thenThrow(new IllegalArgumentException("Запрос не может быть пустым"));

        mockMvc.perform(post("/api/day18/agent")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Запрос не может быть пустым"));
    }

    @Test
    void getHealthMapsMcpFailureToBadGateway() throws Exception {
        when(service.health()).thenThrow(new Day18McpException("MCP недоступен: Connection refused"));

        mockMvc.perform(get("/api/day18/health"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("MCP недоступен: Connection refused"));
    }
}