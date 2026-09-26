package com.yunovan.aiadvent.day20;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/api/day20")
public class Day20Controller {

    private final Day20Orchestrator orchestrator;
    private final Day20AgentService service;

    public Day20Controller(Day20Orchestrator orchestrator, Day20AgentService service) {
        this.orchestrator = orchestrator;
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day20HealthResponse health() {
        return invoke(orchestrator::health);
    }

    @GetMapping("/servers")
    @ResponseBody
    public List<Day20ServerInfo> servers() {
        return invoke(() -> orchestrator.health().servers());
    }

    @GetMapping("/tools")
    @ResponseBody
    public List<Day20ToolEntry> tools() {
        return invoke(orchestrator::tools);
    }

    @GetMapping("/flows")
    @ResponseBody
    public List<Day20FlowDefinition> flows() {
        return invoke(orchestrator::flows);
    }

    @PostMapping("/call")
    @ResponseBody
    public Day20CallResponse call(@RequestBody(required = false) Day20CallRequest request) {
        return invoke(() -> orchestrator.call(
                request == null ? "" : request.server(),
                request == null ? "" : request.tool(),
                request == null ? null : request.arguments()));
    }

    @PostMapping("/route")
    @ResponseBody
    public Day20CallResponse route(@RequestBody(required = false) Day20RouteRequest request) {
        return invoke(() -> orchestrator.route(
                request == null ? "" : request.tool(),
                request == null ? null : request.arguments()));
    }

    @PostMapping("/flow")
    @ResponseBody
    public Day20FlowResponse flow(@RequestBody(required = false) Day20FlowRequest request) {
        return invoke(() -> orchestrator.runFlow(
                request == null ? "" : request.flow(),
                request == null ? null : request.arguments()));
    }

    @PostMapping("/agent")
    @ResponseBody
    public Day20AgentResponse agent(@RequestBody(required = false) Day20AgentRequest request) {
        return invoke(() -> service.submit(request == null ? "" : request.prompt()));
    }

    private static <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Day20McpException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}