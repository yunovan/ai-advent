package com.yunovan.aiadvent.day25;

import com.yunovan.aiadvent.day21.Day21IndexException;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/api/day25")
public class Day25Controller {

    private final Day25ChatService service;

    public Day25Controller(Day25ChatService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day25HealthResponse health() {
        return invoke(service::health);
    }

    @GetMapping("/scenarios")
    @ResponseBody
    public List<Day25Scenario> scenarios() {
        return invoke(service::scenarios);
    }

    @GetMapping("/sessions")
    @ResponseBody
    public List<Day25SessionView> sessions() {
        return invoke(service::sessions);
    }

    @GetMapping("/history")
    @ResponseBody
    public List<Day25Message> history(@RequestParam(name = "sessionId", required = false) String sessionId) {
        return invoke(() -> service.history(sessionId));
    }

    @PostMapping("/chat")
    @ResponseBody
    public Day25ChatTurn chat(@RequestBody(required = false) Day25ChatRequest request) {
        return invoke(() -> service.chat(request == null ? null : request.sessionId(),
                request == null ? null : request.message()));
    }

    @PostMapping("/reset")
    @ResponseBody
    public Day25SessionView reset(@RequestBody(required = false) Day25SessionRequest request) {
        return invoke(() -> {
            service.reset(request == null ? null : request.sessionId());
            return service.memory(request == null ? null : request.sessionId());
        });
    }

    @PostMapping("/evaluate")
    @ResponseBody
    public Day25EvalResponse evaluate() {
        return invoke(service::evaluate);
    }

    private static <T> T invoke(Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (Day21IndexException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }
}
