package com.yunovan.aiadvent.day26;

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
@RequestMapping("/api/day26")
public class Day26Controller {

    private final Day26Service service;

    public Day26Controller(Day26Service service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day26HealthResponse health() {
        return service.health();
    }

    @GetMapping("/tasks")
    @ResponseBody
    public List<Day26Task> tasks() {
        return service.tasks();
    }

    @PostMapping("/run")
    @ResponseBody
    public Day26RunReport run() {
        return service.run();
    }

    @PostMapping("/ask")
    @ResponseBody
    public Day26Answer ask(@RequestBody(required = false) Day26AskRequest request) {
        return invoke(() -> service.ask(request == null ? null : request.prompt()));
    }

    private static <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        } catch (Day26LlmException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ex.getMessage(), ex);
        }
    }
}
