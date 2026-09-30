package com.yunovan.aiadvent.day23;

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
@RequestMapping("/api/day23")
public class Day23Controller {

    private final Day23RagService service;

    public Day23Controller(Day23RagService service) {
        this.service = service;
    }

    @GetMapping("/health")
    @ResponseBody
    public Day23HealthResponse health() {
        return invoke(service::health);
    }

    @GetMapping("/questions")
    @ResponseBody
    public List<Day23ControlQuestion> questions() {
        return invoke(service::questions);
    }

    @PostMapping("/rewrite")
    @ResponseBody
    public Day23RewriteResponse rewrite(@RequestBody(required = false) Day23RewriteRequest request) {
        return invoke(() -> service.rewrite(request == null ? null : request.question()));
    }

    @PostMapping("/answer")
    @ResponseBody
    public Day23AnswerResponse answer(@RequestBody(required = false) Day23AnswerRequest request) {
        return invoke(() -> service.answer(
                request == null ? null : request.question(),
                request == null ? null : request.mode()));
    }

    @PostMapping("/ask")
    @ResponseBody
    public Day23AnswerResponse ask(@RequestBody(required = false) Day23RewriteRequest request) {
        return invoke(() -> service.ask(request == null ? null : request.question()));
    }

    @PostMapping("/compare")
    @ResponseBody
    public Day23CompareResponse compare(@RequestBody(required = false) Day23CompareRequest request) {
        return invoke(() -> service.compare(
                request == null ? null : request.question(),
                request == null ? null : request.firstMode(),
                request == null ? null : request.secondMode()));
    }

    @PostMapping("/evaluate")
    @ResponseBody
    public Day23EvalResponse evaluate() {
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