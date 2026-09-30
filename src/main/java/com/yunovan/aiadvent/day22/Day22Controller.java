package com.yunovan.aiadvent.day22;

import com.yunovan.aiadvent.day21.Day21IndexException;
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
@RequestMapping("/api/day22")
public class Day22Controller {

    private final Day22RagService service;

    public Day22Controller(Day22RagService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day22HealthResponse health() {
        return invoke(service::health);
    }

    @GetMapping("/questions")
    @ResponseBody
    public List<Day22ControlQuestion> questions() {
        return invoke(service::questions);
    }

    @PostMapping("/answer")
    @ResponseBody
    public Day22AnswerResponse answer(@RequestBody(required = false) Day22AnswerRequest request) {
        return invoke(() -> service.answer(
                request == null ? null : request.question(),
                request == null ? null : request.mode()));
    }

    @PostMapping("/ask")
    @ResponseBody
    public Day22AnswerResponse ask(@RequestBody(required = false) Day22AskRequest request) {
        return invoke(() -> service.ask(request == null ? null : request.prompt()));
    }

    @PostMapping("/compare")
    @ResponseBody
    public Day22CompareResponse compare(@RequestBody(required = false) Day22CompareRequest request) {
        return invoke(() -> service.compare(request == null ? null : request.question()));
    }

    @PostMapping("/evaluate")
    @ResponseBody
    public Day22EvalResponse evaluate() {
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