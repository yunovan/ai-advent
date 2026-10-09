package com.yunovan.aiadvent.day28;

import com.yunovan.aiadvent.day22.Day22ControlQuestion;
import com.yunovan.aiadvent.day26.Day26LlmException;
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
@RequestMapping("/api/day28")
public class Day28Controller {

    private final Day28RagService service;

    public Day28Controller(Day28RagService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day28HealthResponse health() {
        return service.health();
    }

    @GetMapping("/questions")
    @ResponseBody
    public List<Day22ControlQuestion> questions() {
        return service.questions();
    }

    @PostMapping("/ask")
    @ResponseBody
    public Day28AnswerResponse ask(@RequestBody(required = false) Day28AskRequest request) {
        return invoke(() -> service.ask(request == null ? null : request.question()));
    }

    @PostMapping("/compare")
    @ResponseBody
    public Day28CompareResponse compare(@RequestBody(required = false) Day28AskRequest request) {
        return invoke(() -> service.compare(request == null ? null : request.question()));
    }

    @PostMapping("/evaluate")
    @ResponseBody
    public Day28EvalResponse evaluate() {
        return invoke(service::evaluate);
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
