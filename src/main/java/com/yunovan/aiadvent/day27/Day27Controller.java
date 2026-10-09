package com.yunovan.aiadvent.day27;

import com.yunovan.aiadvent.day26.Day26LlmException;
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
@RequestMapping("/api/day27")
public class Day27Controller {

    private final Day27ChatService service;

    public Day27Controller(Day27ChatService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day27HealthResponse health() {
        return service.health();
    }

    @GetMapping("/sessions")
    @ResponseBody
    public List<Day27SessionView> sessions() {
        return invoke(service::sessions);
    }

    @GetMapping("/history")
    @ResponseBody
    public List<Day27Message> history(@RequestParam(name = "sessionId", required = false) String sessionId) {
        return invoke(() -> service.history(sessionId));
    }

    @PostMapping("/chat")
    @ResponseBody
    public Day27ChatTurn chat(@RequestBody(required = false) Day27ChatRequest request) {
        return invoke(() -> service.chat(request == null ? null : request.sessionId(),
                request == null ? null : request.message()));
    }

    @PostMapping("/reset")
    @ResponseBody
    public Day27SessionView reset(@RequestBody(required = false) Day27SessionRequest request) {
        return invoke(() -> {
            String sessionId = request == null ? null : request.sessionId();
            service.reset(sessionId);
            return new Day27SessionView(sessionId == null || sessionId.isBlank() ? "default" : sessionId.trim(),
                    0, 0, null);
        });
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
