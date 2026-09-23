package com.yunovan.aiadvent.day16;

import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/api/day16")
public class Day16McpController {

    private static final Logger log = LoggerFactory.getLogger(Day16McpController.class);

    private final Day16McpService service;

    public Day16McpController(Day16McpService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day16HealthResponse health() {
        return invoke(() -> service.health());
    }

    @GetMapping("/tools")
    @ResponseBody
    public List<Day16ToolInfo> tools() {
        return invoke(service::tools);
    }

    @PostMapping("/call")
    @ResponseBody
    public Day16CallResponse call(@RequestBody(required = false) Day16CallRequest request) {
        return invoke(() -> service.call(
                request == null || request.tool() == null ? "" : request.tool(),
                request == null ? null : request.arguments()));
    }

    private <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Day16McpException ex) {
            log.warn("День 16: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}