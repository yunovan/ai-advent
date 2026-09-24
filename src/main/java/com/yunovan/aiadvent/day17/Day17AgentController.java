package com.yunovan.aiadvent.day17;

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
@RequestMapping("/api/day17")
public class Day17AgentController {

    private final Day17AgentService service;

    public Day17AgentController(Day17AgentService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day17HealthResponse health() {
        return invoke(service::health);
    }

    @GetMapping("/tools")
    @ResponseBody
    public List<Day17ToolInfo> tools() {
        return invoke(service::tools);
    }

    @PostMapping("/agent")
    @ResponseBody
    public Day17AgentResponse agent(@RequestBody(required = false) Day17AgentRequest request) {
        return invoke(() -> service.submit(request == null ? "" : request.prompt()));
    }

    private static <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Day17McpException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}