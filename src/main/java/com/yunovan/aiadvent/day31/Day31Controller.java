package com.yunovan.aiadvent.day31;

import com.yunovan.aiadvent.day26.Day26LlmException;
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
@RequestMapping("/api/day31")
public class Day31Controller {

    private final Day31DeveloperAssistantService service;

    public Day31Controller(Day31DeveloperAssistantService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day31HealthResponse health() {
        return service.health();
    }

    @GetMapping("/docs")
    @ResponseBody
    public java.util.List<Day31DocumentInfo> docs() {
        return service.docs();
    }

    @GetMapping("/tools")
    @ResponseBody
    public Day31ToolsResponse tools() {
        return service.tools();
    }

    @PostMapping("/git")
    @ResponseBody
    public Day31GitResponse git(@RequestBody(required = false) Day31GitRequest request) {
        return invoke(() -> service.git(
                request == null ? null : request.tool(),
                request == null ? null : request.arguments()));
    }

    @PostMapping("/help")
    @ResponseBody
    public Day31HelpResponse help(@RequestBody(required = false) Day31HelpRequest request) {
        return invoke(() -> service.help(request == null ? null : request.question()));
    }

    private static <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Day31GitException | Day31McpException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        } catch (Day26LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}
