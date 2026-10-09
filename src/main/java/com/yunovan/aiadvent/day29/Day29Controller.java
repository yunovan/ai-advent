package com.yunovan.aiadvent.day29;

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
@RequestMapping("/api/day29")
public class Day29Controller {

    private final Day29OptimizationService service;

    public Day29Controller(Day29OptimizationService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day29HealthResponse health() {
        return service.health();
    }

    @PostMapping("/ask")
    @ResponseBody
    public Day29AskResponse ask(@RequestBody(required = false) Day29AskRequest request) {
        return invoke(() -> service.ask(request == null ? null : request.question()));
    }

    @PostMapping("/run")
    @ResponseBody
    public Day29RunResponse run() {
        return invoke(service::run);
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
